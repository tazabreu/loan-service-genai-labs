package com.example.loan.api.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {
    List<OutboxEventEntity> findTop100BySentFalseOrderByCreatedAtAsc();
}

