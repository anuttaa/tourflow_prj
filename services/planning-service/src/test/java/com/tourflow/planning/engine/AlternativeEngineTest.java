package com.tourflow.planning.engine;

import static org.junit.jupiter.api.Assertions.*;

import com.tourflow.planning.engine.AlternativeEngine.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AlternativeEngineTest {
    static final String Z = "Europe/Istanbul";
    static Instant at(String s) { return LocalDateTime.parse(s).atZone(ZoneId.of(Z)).toInstant(); }
    static Seg seg(long id, String s, String e) {
        return new Seg(id, SegType.EXCURSION, at(s), at(e), Z, 41.0, 29.0, 41.0, 29.0, false, null, null, Set.of(), null, null, false, "Экскурсия", 100, 50, false);
    }
    static Cand cand(long ref, String s, String e, String price, double rating, int cls) {
        return new Cand(ref, seg(7, s, e), new BigDecimal(price), rating, cls);
    }
    static final Seg ORIG = seg(7, "2026-12-11T10:00", "2026-12-11T13:00");

    @Test void weightsSumToOne() {
        for (String p : List.of("ECONOMY", "BALANCE", "COMFORT")) {
            Weights w = AlternativeEngine.weights(p);
            assertEquals(1.0, w.price() + w.time() + w.rating() + w.proximity() + w.similarity(), 1e-9);
        }
    }
    @Test void economyProfilePrefersCheapest() {
        List<Cand> c = List.of(cand(1, "2026-12-11T10:00", "2026-12-11T13:00", "50", 4.5, 3), cand(2, "2026-12-11T10:00", "2026-12-11T13:00", "20", 4.5, 3));
        List<Scored> r = AlternativeEngine.rank(c, ORIG, 3, new BigDecimal("35"), null, null, 180, AlternativeEngine.weights("ECONOMY"), 3, x -> true);
        assertEquals(2L, r.get(0).cand().offerRef());
        assertEquals(1, r.get(0).rank());
    }
    @Test void comfortProfilePrefersRating() {
        List<Cand> c = List.of(cand(1, "2026-12-11T10:00", "2026-12-11T13:00", "20", 3.0, 3), cand(2, "2026-12-11T10:00", "2026-12-11T13:00", "30", 5.0, 3));
        List<Scored> r = AlternativeEngine.rank(c, ORIG, 3, new BigDecimal("25"), null, null, 180, AlternativeEngine.weights("COMFORT"), 3, x -> true);
        assertEquals(2L, r.get(0).cand().offerRef());
    }
    @Test void closerStartTimeScoresHigher() {
        List<Cand> c = List.of(cand(1, "2026-12-11T12:00", "2026-12-11T15:00", "30", 4.0, 3), cand(2, "2026-12-11T10:30", "2026-12-11T13:30", "30", 4.0, 3));
        List<Scored> r = AlternativeEngine.rank(c, ORIG, 3, new BigDecimal("30"), null, null, 180, AlternativeEngine.weights("BALANCE"), 3, x -> true);
        assertEquals(2L, r.get(0).cand().offerRef());
        assertEquals(30L, r.get(0).deltaStartMin());
    }
    @Test void infeasibleCandidatesAreDropped() {
        List<Cand> c = List.of(cand(1, "2026-12-11T10:00", "2026-12-11T13:00", "20", 4.0, 3), cand(2, "2026-12-11T10:00", "2026-12-11T13:00", "30", 4.0, 3));
        List<Scored> r = AlternativeEngine.rank(c, ORIG, 3, new BigDecimal("30"), null, null, 180, AlternativeEngine.weights("BALANCE"), 3, x -> x.offerRef() == 2);
        assertEquals(1, r.size());
        assertEquals(2L, r.get(0).cand().offerRef());
    }
    @Test void emptyWhenNothingFeasible() {
        List<Cand> c = List.of(cand(1, "2026-12-11T10:00", "2026-12-11T13:00", "20", 4.0, 3));
        assertTrue(AlternativeEngine.rank(c, ORIG, 3, new BigDecimal("30"), null, null, 180, AlternativeEngine.weights("BALANCE"), 3, x -> false).isEmpty());
    }
    @Test void topNLimitsResultAndExplainsPriceDelta() {
        List<Cand> c = List.of(cand(1, "2026-12-11T10:00", "2026-12-11T13:00", "20", 4.0, 3), cand(2, "2026-12-11T10:00", "2026-12-11T13:00", "25", 4.0, 3),
                cand(3, "2026-12-11T10:00", "2026-12-11T13:00", "40", 4.0, 3));
        List<Scored> r = AlternativeEngine.rank(c, ORIG, 3, new BigDecimal("30"), null, null, 180, AlternativeEngine.weights("ECONOMY"), 2, x -> true);
        assertEquals(2, r.size());
        assertEquals(new BigDecimal("-10"), r.get(0).deltaPrice());
        assertTrue(r.get(0).explanation().contains("цена"));
    }
}
