package com.example.loan.api.it;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.redpanda.RedpandaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class LoanFullLifecycleIT {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static RedpandaContainer redpanda = new RedpandaContainer(DockerImageName.parse("redpandadata/redpanda:latest"));

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.kafka.bootstrap-servers", () -> sanitize(redpanda.getBootstrapServers()));
        r.add("outbox.enabled", () -> true);
        r.add("outbox.polling.interval", () -> 200);
        r.add("kafka.topics.auto-create", () -> true);
    }

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate http;

    @Test
    void fullLifecycleEmitsKafkaEvents() {
        // simulate -> approved
        String simulateBody = "{\"amount\":5000,\"termMonths\":12,\"interestRate\":0.12,\"customerId\":\"e2e\"}";
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        var simulateResp = http.postForEntity("http://localhost:" + port + "/loans/simulate", new HttpEntity<>(simulateBody, h), String.class);
        assertThat(simulateResp.getStatusCode().is2xxSuccessful()).isTrue();
        String id = extractId(simulateResp.getBody());
        assertThat(id).isNotEmpty();

        // contract -> disburse -> pay
        http.postForEntity("http://localhost:" + port + "/loans/" + id + "/contract", null, String.class);
        http.postForEntity("http://localhost:" + port + "/loans/" + id + "/disburse", null, String.class);
        http.postForEntity("http://localhost:" + port + "/loans/" + id + "/pay", new HttpEntity<>("{\"amount\":5000}", h), String.class);

        // assert Kafka events
        List<ConsumerRecord<String, String>> received = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps())) {
            consumer.subscribe(List.of("loan-events"));
            long deadline = System.currentTimeMillis() + 20000;
            while (System.currentTimeMillis() < deadline && received.size() < 5) {
                consumer.poll(Duration.ofMillis(500)).forEach(received::add);
            }
        }
        long count = received.stream().map(ConsumerRecord::value).filter(v -> v != null && v.contains(id)).count();
        assertThat(count).isGreaterThanOrEqualTo(5);
    }

    private static Properties consumerProps() {
        Properties p = new Properties();
        p.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, sanitize(redpanda.getBootstrapServers()));
        p.put(ConsumerConfig.GROUP_ID_CONFIG, "e2e-" + UUID.randomUUID());
        p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        p.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return p;
    }

    private static String extractId(String json) {
        if (json == null) return "";
        int idx = json.indexOf("\"id\":");
        if (idx < 0) return "";
        int start = json.indexOf('"', idx + 5);
        int end = json.indexOf('"', start + 1);
        return (start > 0 && end > start) ? json.substring(start + 1, end) : "";
    }

    private static String sanitize(String url) {
        if (url == null) return "";
        return url.replace("PLAINTEXT://", "").replace("SASL_PLAINTEXT://", "");
    }
}
