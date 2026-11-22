package com.tourflow.common.outbox;

import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Отдельный поток публикует накопленные события в Kafka; при недоступности Kafka события остаются в outbox. */
@Component
@EnableScheduling
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private final OutboxRepository repo;
    private final KafkaTemplate<String, String> kafka;

    public OutboxPublisher(OutboxRepository repo, KafkaTemplate<String, String> kafka) { this.repo = repo; this.kafka = kafka; }

    @Scheduled(fixedDelayString = "${app.outbox.delay-ms:1000}")
    @Transactional
    public void flush() {
        for (OutboxEvent e : repo.findTop100ByPublishedAtIsNullOrderByIdAsc()) {
            try {
                kafka.send(e.getTopic(), e.getEventKey(), e.getPayload()).get(5, TimeUnit.SECONDS);
                e.setPublishedAt(Instant.now());
            } catch (Exception ex) {
                log.warn("Kafka недоступна, событие {} останется в outbox: {}", e.getId(), ex.toString());
                return;
            }
        }
    }
}
