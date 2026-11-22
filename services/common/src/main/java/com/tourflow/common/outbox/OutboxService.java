package com.tourflow.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tourflow.common.events.EventEnvelope;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxService {
    private final OutboxRepository repo;
    private final ObjectMapper mapper;

    public OutboxService(OutboxRepository repo, ObjectMapper mapper) { this.repo = repo; this.mapper = mapper; }

    /** Вызывать внутри транзакции бизнес-операции. topic совпадает с eventType. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(String topic, String key, Object payload) {
        try {
            EventEnvelope env = new EventEnvelope(UUID.randomUUID().toString(), topic, Instant.now(),
                    MDC.get("correlationId"), mapper.valueToTree(payload));
            OutboxEvent e = new OutboxEvent();
            e.setTopic(topic);
            e.setEventKey(key);
            e.setPayload(mapper.writeValueAsString(env));
            repo.save(e);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Не удалось сериализовать событие " + topic, ex);
        }
    }
}
