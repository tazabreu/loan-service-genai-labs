package com.example.loan.api.it;

import com.example.loan.api.domain.service.LoanService;
import com.example.loan.api.persistence.OutboxEventRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class LoanOutboxIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final int HOST_KAFKA_PORT = 19092;

    @Container
    static GenericContainer<?> redpanda = new GenericContainer<>(DockerImageName.parse("redpandadata/redpanda:latest"))
            .withExposedPorts(9092)
            .withCreateContainerCmdModifier(cmd ->
                    cmd.getHostConfig().withPortBindings(new PortBinding(Ports.Binding.bindPort(HOST_KAFKA_PORT), new ExposedPort(9092))))
            .withCommand(
                    "redpanda start --overprovisioned --smp 1 --memory 256M --reserve-memory 0M --check=false " +
                    "--kafka-addr PLAINTEXT://0.0.0.0:9092 " +
                    "--advertise-kafka-addr PLAINTEXT://127.0.0.1:" + HOST_KAFKA_PORT
            );

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.kafka.bootstrap-servers", () -> "127.0.0.1:" + HOST_KAFKA_PORT);
        r.add("outbox.enabled", () -> true);
        r.add("outbox.polling.interval", () -> 200);
        r.add("kafka.topics.auto-create", () -> true);
    }

    @Autowired
    LoanService service;
    @Autowired
    OutboxEventRepository outbox;

    @Test
    void simulate_publishesEventsToKafka() {
        service.simulate(new BigDecimal("5000"), 12, new BigDecimal("0.1"), "c-it", false);

        // consume from Kafka and assert at least 2 events published
        List<ConsumerRecord<String, String>> received = new CopyOnWriteArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps())) {
            consumer.subscribe(List.of("loan-events"));
            long deadline = System.currentTimeMillis() + 15000;
            while (System.currentTimeMillis() < deadline && received.size() < 2) {
                consumer.poll(Duration.ofMillis(500)).forEach(received::add);
            }
        }
        assertThat(received.size()).isGreaterThanOrEqualTo(2);
    }

    private static Properties consumerProps() {
        Properties p = new Properties();
        p.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "127.0.0.1:" + HOST_KAFKA_PORT);
        p.put(ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID());
        p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        p.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return p;
        }
}
