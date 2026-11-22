package com.tourflow.planning.engine;

import java.util.HashMap;
import java.util.Map;

/** Параметры правил VR-01…VR-10 (хранятся в таблице validation_rules, ТЗ 7.1). */
public final class Rules {
    private final Map<String, Double> values;

    public Rules(Map<String, Double> overrides) {
        values = new HashMap<>(defaults());
        if (overrides != null) values.putAll(overrides);
    }

    public static Map<String, Double> defaults() {
        Map<String, Double> m = new HashMap<>();
        m.put("BUF_FLIGHT_FLIGHT_INTL", 120.0);
        m.put("BUF_FLIGHT_FLIGHT_DOM", 90.0);
        m.put("BUF_FLIGHT_ANY", 45.0);
        m.put("BUF_ANY_FLIGHT", 90.0);
        m.put("BUF_STATION_ANY", 30.0);
        m.put("BUF_TRANSFER_ANY", 15.0);
        m.put("BUF_DEFAULT", 10.0);
        m.put("SPEED_KMH", 30.0);
        m.put("WARN_SLACK_RATIO", 0.25);
        m.put("CHECKIN_FROM", 840.0);
        m.put("CHECKOUT_UNTIL", 720.0);
        m.put("MAX_DAILY_ACTIVE_MIN", 600.0);
        m.put("MIN_BREAK_MIN", 60.0);
        m.put("BUDGET_TOLERANCE", 0.10);
        return m;
    }

    public double get(String key) { return values.get(key); }
}
