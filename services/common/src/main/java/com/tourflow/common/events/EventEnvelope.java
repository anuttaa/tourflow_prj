package com.tourflow.common.events;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/** Единый формат события Kafka (ТЗ 10.3). */
public record EventEnvelope(String eventId, String eventType, Instant occurredAt, String correlationId, JsonNode payload) {}
