package com.tourflow.common.outbox;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** Transactional outbox: событие пишется в той же транзакции, что и бизнес-данные (ТЗ 7.6, 9.3). */
@Entity
@Table(name = "outbox_events")
@Getter @Setter
public class OutboxEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private String topic;
    @Column(name = "event_key") private String eventKey;
    @Column(nullable = false, columnDefinition = "text") private String payload;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
    @Column(name = "published_at") private Instant publishedAt;
}
