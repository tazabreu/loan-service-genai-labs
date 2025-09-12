package com.example.loan.api.config;

import com.example.loan.api.persistence.LoanRepository;
import com.example.loan.events.model.LoanStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@EnableScheduling
public class LoanMetrics {
    private final LoanRepository repo;
    private final Map<LoanStatus, AtomicInteger> gauges = new EnumMap<>(LoanStatus.class);

    public LoanMetrics(LoanRepository repo, MeterRegistry registry) {
        this.repo = repo;
        for (LoanStatus s : LoanStatus.values()) {
            AtomicInteger ref = new AtomicInteger(0);
            gauges.put(s, ref);
            Gauge.builder("loans_by_status", ref, AtomicInteger::get)
                    .description("Current number of loans by status")
                    .tag("status", s.name())
                    .register(registry);
        }
        refresh();
    }

    @Scheduled(fixedDelayString = "${loan.metrics.refresh.ms:30000}")
    public void refresh() {
        for (LoanStatus s : LoanStatus.values()) {
            long count = repo.countByStatus(s);
            AtomicInteger ref = gauges.get(s);
            if (ref != null) ref.set((int) Math.min(count, Integer.MAX_VALUE));
        }
    }
}

