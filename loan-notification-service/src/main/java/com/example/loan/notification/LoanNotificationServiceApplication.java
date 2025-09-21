package com.example.loan.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication
@EnableKafka
public class LoanNotificationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(LoanNotificationServiceApplication.class, args);
    }
}
