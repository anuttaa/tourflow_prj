package com.tourflow.planning.engine;

import java.time.Instant;
import java.util.Set;

/** Сегмент маршрута для движка валидации (время всегда в UTC + IANA-зона, VR-03). */
public record Seg(long id, SegType type, Instant start, Instant end, String tz,
                  Double fromLat, Double fromLon, Double toLat, Double toLon,
                  boolean international, Integer openFrom, Integer openTo, Set<Integer> openDays,
                  Integer checkInFrom, Integer checkOutUntil, boolean earlyCheckIn, String title,
                  Integer capacity, Integer free, boolean stale) {

    public Seg with(long newId, Instant s, Instant e) {
        return new Seg(newId, type, s, e, tz, fromLat, fromLon, toLat, toLon, international, openFrom, openTo, openDays,
                checkInFrom, checkOutUntil, earlyCheckIn, title, capacity, free, stale);
    }
}
