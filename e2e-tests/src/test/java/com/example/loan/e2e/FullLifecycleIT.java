package com.example.loan.e2e;

import com.example.loan.api.LoanApiApplication;
import com.example.loan.notification.LoanNotificationServiceApplication;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.web.client.RestClient;
import org.testcontainers.redpanda.RedpandaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.URI;
import java.time.Duration;
import java.util.*;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class FullLifecycleIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("loans")
            .withUsername("postgres")
            .withPassword("postgres");

    @Container
    static RedpandaContainer kafka = new RedpandaContainer(DockerImageName.parse("redpandadata/redpanda:latest"));

    static ConfigurableApplicationContext apiCtx;
    static ConfigurableApplicationContext notifCtx;

    @BeforeAll
    static void startApps() {
        // Ensure containers are fully started before getting connection details
        String kafkaBootstrap = kafka.getBootstrapServers();
        String pgUrl = postgres.getJdbcUrl();
        String pgUser = postgres.getUsername();
        String pgPassword = postgres.getPassword();
        
        // Start notification service first so it consumes from earliest
        notifCtx = new SpringApplicationBuilder(LoanNotificationServiceApplication.class)
                .web(WebApplicationType.SERVLET)
                .properties(Map.of(
                        "server.port", "0",
                        "spring.kafka.bootstrap-servers", kafkaBootstrap,
                        "spring.kafka.consumer.auto-offset-reset", "earliest"
                ))
                .run();

        Map<String, Object> apiProps = new HashMap<>();
        apiProps.put("server.port", 0);
        apiProps.put("spring.datasource.url", pgUrl);
        apiProps.put("spring.datasource.username", pgUser);
        apiProps.put("spring.datasource.password", pgPassword);
        apiProps.put("spring.datasource.driver-class-name", "org.postgresql.Driver");
        apiProps.put("spring.kafka.bootstrap-servers", kafkaBootstrap);
        apiProps.put("outbox.enabled", true);
        apiProps.put("outbox.polling.interval", 200);
        apiProps.put("kafka.topics.auto-create", true);
        apiProps.put("spring.flyway.enabled", true);
        apiProps.put("spring.jpa.hibernate.ddl-auto", "create-drop");
        
        apiCtx = new SpringApplicationBuilder(LoanApiApplication.class)
                .web(WebApplicationType.SERVLET)
                .properties(apiProps)
                .run();
    }

    @AfterAll
    static void stopApps() {
        if (apiCtx != null) apiCtx.close();
        if (notifCtx != null) notifCtx.close();
    }

    @Test
    void fullLifecycle_emitsKafkaEvents() {
        int port = ((WebServerApplicationContext) apiCtx).getWebServer().getPort();
        RestClient http = RestClient.create();

        // simulate -> approved (auto)
        String simulateBody = "{\"amount\":5000,\"termMonths\":12,\"interestRate\":0.12,\"customerId\":\"e2e\"}";
        var simulateResp = http.post().uri(URI.create("http://localhost:" + port + "/loans/simulate"))
                .contentType(MediaType.APPLICATION_JSON).body(simulateBody).retrieve().toEntity(String.class);
        assertThat(simulateResp.getStatusCode().is2xxSuccessful()).isTrue();
        String loanId = extractId(simulateResp.getBody());
        assertThat(loanId).isNotEmpty();

        // contract -> disburse -> pay
        http.post().uri("http://localhost:" + port + "/loans/" + loanId + "/contract").retrieve().toBodilessEntity();
        http.post().uri("http://localhost:" + port + "/loans/" + loanId + "/disburse").retrieve().toBodilessEntity();
        http.post().uri("http://localhost:" + port + "/loans/" + loanId + "/pay")
                .contentType(MediaType.APPLICATION_JSON).body("{\"amount\":5000}")
                .retrieve().toBodilessEntity();

        // consume from Kafka
        List<ConsumerRecord<String, String>> records = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps())) {
            consumer.subscribe(List.of("loan-events"));
            long deadline = System.currentTimeMillis() + 20000;
            while (System.currentTimeMillis() < deadline && records.size() < 5) {
                consumer.poll(Duration.ofMillis(500)).forEach(records::add);
            }
        }
        long matched = records.stream().map(ConsumerRecord::value).filter(v -> v != null && v.contains(loanId)).count();
        assertThat(matched).isGreaterThanOrEqualTo(5);
    }

    private static Properties consumerProps() {
        Properties p = new Properties();
        p.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
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
}


