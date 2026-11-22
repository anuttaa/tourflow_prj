package com.tourflow.planning;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tourflow.common.events.EventEnvelope;
import com.tourflow.planning.repository.TourSegmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** РўР— 10.3: РёР·РјРµРЅРµРЅРёСЏ РєР°С‚Р°Р»РѕРіР° РїРѕРјРµС‡Р°СЋС‚ СЃРµРіРјРµРЅС‚С‹ РєР°Рє СѓСЃС‚Р°СЂРµРІС€РёРµ (stale), Рё РІР°Р»РёРґР°С†РёСЏ РїСЂРµРґСѓРїСЂРµР¶РґР°РµС‚ РјРµРЅРµРґР¶РµСЂР°. */
@Component
public class CatalogEventsListener {
    private static final Logger log = LoggerFactory.getLogger(CatalogEventsListener.class);
    private final TourSegmentRepository segments; private final ObjectMapper mapper;

    public CatalogEventsListener(TourSegmentRepository segments, ObjectMapper mapper) { this.segments = segments; this.mapper = mapper; }

    @KafkaListener(topics = {"offer.updated", "availability.changed"}, groupId = "planning")
    @Transactional
    public void onCatalogEvent(String message) {
        try {
            EventEnvelope e = mapper.readValue(message, EventEnvelope.class);
            long offerId = e.payload().get("offerId").asLong();
            int n = segments.markStale(offerId);
            log.info("{}: РѕРїС†РёСЏ {} РёР·РјРµРЅРёР»Р°СЃСЊ, РїРѕРјРµС‡РµРЅРѕ СЃРµРіРјРµРЅС‚РѕРІ: {}", e.eventType(), offerId, n);
        } catch (Exception ex) {
            log.warn("РќРµ СѓРґР°Р»РѕСЃСЊ РѕР±СЂР°Р±РѕС‚Р°С‚СЊ СЃРѕР±С‹С‚РёРµ РєР°С‚Р°Р»РѕРіР°: {}", ex.toString());
        }
    }
}

