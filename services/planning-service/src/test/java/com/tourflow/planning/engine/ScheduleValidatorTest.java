package com.tourflow.planning.engine;

import static org.junit.jupiter.api.Assertions.*;

import com.tourflow.planning.engine.ScheduleValidator.Result;
import com.tourflow.planning.engine.ScheduleValidator.TourParams;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Модульные тесты правил VR-01…VR-10 и BR-01 (минимум два теста на правило: нарушение и норма, ТЗ 13). */
class ScheduleValidatorTest {
    static final String Z = "Europe/Istanbul";
    static final Rules RULES = new Rules(null);

    static java.time.Instant at(String s) { return LocalDateTime.parse(s).atZone(ZoneId.of(Z)).toInstant(); }

    static Seg seg(long id, SegType t, String s, String e, String title) {
        return new Seg(id, t, at(s), at(e), Z, null, null, null, null, false, null, null, Set.of(), null, null, false, title, 100, 50, false);
    }
    static Seg geo(long id, SegType t, String s, String e, double fromLat, double toLat) {
        return new Seg(id, t, at(s), at(e), Z, fromLat, 29.0, toLat, 29.0, false, null, null, Set.of(), null, null, false, "S" + id, 100, 50, false);
    }
    static Seg open(long id, String s, String e, int from, int to, Set<Integer> days) {
        return new Seg(id, SegType.EXCURSION, at(s), at(e), Z, null, null, null, null, false, from, to, days, null, null, false, "Экскурсия", 100, 50, false);
    }
    static Seg hotel(long id, String s, String e, boolean early) {
        return new Seg(id, SegType.HOTEL, at(s), at(e), Z, null, null, null, null, false, null, null, Set.of(), null, null, early, "Отель", 100, 50, false);
    }
    static TourParams tour() { return new TourParams(LocalDate.of(2026, 12, 10), LocalDate.of(2026, 12, 13), Z, 2, null, null); }

    static boolean has(Result r, String rule, Severity s) { return r.conflicts().stream().anyMatch(c -> c.rule().equals(rule) && c.severity() == s); }
    static boolean none(Result r, String rule) { return r.conflicts().stream().noneMatch(c -> c.rule().equals(rule)); }
    static Result run(Seg... segs) { return ScheduleValidator.validate(tour(), List.of(segs), RULES); }

    @Test void vr01_overlapIsError() {
        Result r = run(seg(1, SegType.EXCURSION, "2026-12-11T10:00", "2026-12-11T13:00", "A"), seg(2, SegType.EXCURSION, "2026-12-11T12:00", "2026-12-11T14:00", "B"));
        assertTrue(has(r, "VR-01", Severity.ERROR));
        assertEquals(60L, r.conflicts().stream().filter(c -> c.rule().equals("VR-01")).findFirst().get().violationMin());
    }
    @Test void vr01_hotelMayOverlapActivities() {
        assertTrue(none(run(hotel(1, "2026-12-10T15:00", "2026-12-13T11:00", false), seg(2, SegType.EXCURSION, "2026-12-11T10:00", "2026-12-11T13:00", "A")), "VR-01"));
    }
    @Test void vr02_shortTransferAfterFlightIsError() {
        Result r = run(seg(1, SegType.FLIGHT, "2026-12-10T10:00", "2026-12-10T13:00", "F"), seg(2, SegType.TRANSFER, "2026-12-10T13:30", "2026-12-10T14:20", "T"));
        assertTrue(has(r, "VR-02", Severity.ERROR));
    }
    @Test void vr02_enoughGapIsOk() {
        Result r = run(seg(1, SegType.FLIGHT, "2026-12-10T10:00", "2026-12-10T13:00", "F"), seg(2, SegType.TRANSFER, "2026-12-10T14:00", "2026-12-10T14:50", "T"));
        assertTrue(none(r, "VR-02"));
    }
    @Test void vr02_smallSlackIsWarning() {
        Result r = run(seg(1, SegType.FLIGHT, "2026-12-10T10:00", "2026-12-10T13:00", "F"), seg(2, SegType.TRANSFER, "2026-12-10T13:50", "2026-12-10T14:40", "T"));
        assertTrue(has(r, "VR-02", Severity.WARNING));
        assertFalse(has(r, "VR-02", Severity.ERROR));
    }
    @Test void vr02_internationalConnectionNeeds120() {
        Seg f1 = new Seg(1, SegType.FLIGHT, at("2026-12-10T08:00"), at("2026-12-10T10:00"), Z, null, null, null, null, true, null, null, Set.of(), null, null, false, "F1", 100, 50, false);
        Seg f2bad = new Seg(2, SegType.FLIGHT, at("2026-12-10T11:40"), at("2026-12-10T13:00"), Z, null, null, null, null, true, null, null, Set.of(), null, null, false, "F2", 100, 50, false);
        Seg f2ok = f2bad.with(2, at("2026-12-10T12:10"), at("2026-12-10T13:30"));
        assertTrue(has(run(f1, f2bad), "VR-02", Severity.ERROR));
        assertFalse(has(run(f1, f2ok), "VR-02", Severity.ERROR));
    }
    @Test void vr03_endBeforeStartIsError() {
        Seg bad = seg(1, SegType.EXCURSION, "2026-12-11T10:00", "2026-12-11T10:30", "A").with(1, at("2026-12-11T12:00"), at("2026-12-11T11:00"));
        assertTrue(has(run(bad), "VR-03", Severity.ERROR));
    }
    @Test void vr03_unknownTimezoneIsError() {
        Seg s = new Seg(1, SegType.EXCURSION, at("2026-12-11T10:00"), at("2026-12-11T12:00"), "Mars/Base", null, null, null, null, false, null, null, Set.of(), null, null, false, "A", 100, 50, false);
        assertTrue(has(run(s), "VR-03", Severity.ERROR));
    }
    @Test void vr04_travelTimeTooLong() {
        // ~60 км между точками при скорости 30 км/ч — нужно 120 мин, есть 30
        Result r = run(geo(1, SegType.EXCURSION, "2026-12-11T10:00", "2026-12-11T12:00", 41.0, 41.0), geo(2, SegType.EXCURSION, "2026-12-11T12:30", "2026-12-11T14:00", 41.54, 41.54));
        assertTrue(has(r, "VR-04", Severity.ERROR));
    }
    @Test void vr04_samePlaceIsOk() {
        Result r = run(geo(1, SegType.EXCURSION, "2026-12-11T10:00", "2026-12-11T12:00", 41.0, 41.0), geo(2, SegType.EXCURSION, "2026-12-11T13:30", "2026-12-11T15:00", 41.0, 41.0));
        assertTrue(none(r, "VR-04"));
    }
    @Test void vr05_beforeOpeningIsError() {
        assertTrue(has(run(open(1, "2026-12-11T08:00", "2026-12-11T10:00", 540, 1020, Set.of())), "VR-05", Severity.ERROR));
    }
    @Test void vr05_afterClosingAndClosedDay() {
        assertTrue(has(run(open(1, "2026-12-11T16:00", "2026-12-11T18:00", 540, 1020, Set.of())), "VR-05", Severity.ERROR));
        // 2026-12-14 — понедельник, объект работает только со вторника
        assertTrue(has(run(open(1, "2026-12-14T10:00", "2026-12-14T12:00", 540, 1020, Set.of(2, 3, 4, 5, 6, 7))), "VR-05", Severity.ERROR));
    }
    @Test void vr05_withinHoursIsOk() {
        assertTrue(none(run(open(1, "2026-12-11T10:00", "2026-12-11T13:00", 540, 1020, Set.of())), "VR-05"));
    }
    @Test void vr06_earlyCheckInIsErrorOrWarning() {
        assertTrue(has(run(hotel(1, "2026-12-10T11:00", "2026-12-13T11:00", false)), "VR-06", Severity.ERROR));
        Result r = run(hotel(1, "2026-12-10T11:00", "2026-12-13T11:00", true));
        assertTrue(has(r, "VR-06", Severity.WARNING));
        assertFalse(has(r, "VR-06", Severity.ERROR));
    }
    @Test void vr06_lateCheckOutIsError() {
        assertTrue(has(run(hotel(1, "2026-12-10T15:00", "2026-12-13T13:00", false)), "VR-06", Severity.ERROR));
        assertTrue(none(run(hotel(1, "2026-12-10T15:00", "2026-12-13T11:30", false)), "VR-06"));
    }
    @Test void vr07_uncoveredNightIsError() {
        Result r = run(hotel(1, "2026-12-10T15:00", "2026-12-12T11:00", false));
        assertEquals(1L, r.conflicts().stream().filter(c -> c.rule().equals("VR-07")).count());
    }
    @Test void vr07_allNightsCoveredIsOk() {
        assertTrue(none(run(hotel(1, "2026-12-10T15:00", "2026-12-13T11:00", false)), "VR-07"));
    }
    @Test void vr08_outsideTourDatesIsError() {
        assertTrue(has(run(seg(1, SegType.FLIGHT, "2026-12-09T10:00", "2026-12-09T13:00", "F")), "VR-08", Severity.ERROR));
    }
    @Test void vr08_firstSegmentMustBeArrivalAndTourNotEmpty() {
        assertTrue(has(run(seg(1, SegType.EXCURSION, "2026-12-10T10:00", "2026-12-10T12:00", "A")), "VR-08", Severity.ERROR));
        assertTrue(has(run(), "VR-08", Severity.ERROR));
    }
    @Test void vr09_notEnoughFreePlacesIsError() {
        Seg s = new Seg(1, SegType.EXCURSION, at("2026-12-11T10:00"), at("2026-12-11T12:00"), Z, null, null, null, null, false, null, null, Set.of(), null, null, false, "A", 100, 1, false);
        assertTrue(has(run(s), "VR-09", Severity.ERROR));
    }
    @Test void vr09_unknownAvailabilityIsWarningAndCapacityChecked() {
        Seg unknown = new Seg(1, SegType.EXCURSION, at("2026-12-11T10:00"), at("2026-12-11T12:00"), Z, null, null, null, null, false, null, null, Set.of(), null, null, false, "A", 100, null, false);
        Result r = run(unknown);
        assertTrue(has(r, "VR-09", Severity.WARNING));
        Seg small = new Seg(1, SegType.EXCURSION, at("2026-12-11T10:00"), at("2026-12-11T12:00"), Z, null, null, null, null, false, null, null, Set.of(), null, null, false, "A", 1, 50, false);
        assertTrue(has(run(small), "VR-09", Severity.ERROR));
    }
    @Test void vr10_dailyLoadWarning() {
        Result r = run(seg(1, SegType.EXCURSION, "2026-12-11T07:00", "2026-12-11T13:00", "A"), seg(2, SegType.EXCURSION, "2026-12-11T14:00", "2026-12-11T19:00", "B"));
        assertTrue(has(r, "VR-10", Severity.WARNING));
    }
    @Test void vr10_normalDayHasNoLoadWarning() {
        Result r = run(seg(1, SegType.EXCURSION, "2026-12-11T10:00", "2026-12-11T13:00", "A"));
        assertTrue(none(r, "VR-10"));
    }
    @Test void br01_budgetWarningThenError() {
        TourParams warn = new TourParams(LocalDate.of(2026, 12, 10), LocalDate.of(2026, 12, 13), Z, 2, new BigDecimal("1000"), new BigDecimal("1050"));
        TourParams err = new TourParams(LocalDate.of(2026, 12, 10), LocalDate.of(2026, 12, 13), Z, 2, new BigDecimal("1000"), new BigDecimal("1200"));
        assertTrue(has(ScheduleValidator.validate(warn, List.of(), RULES), "BR-01", Severity.WARNING));
        assertTrue(has(ScheduleValidator.validate(err, List.of(), RULES), "BR-01", Severity.ERROR));
    }
    @Test void referenceTourHasNoErrors() {
        Result r = run(
                seg(1, SegType.FLIGHT, "2026-12-10T10:00", "2026-12-10T13:00", "Рейс туда"),
                seg(2, SegType.TRANSFER, "2026-12-10T14:00", "2026-12-10T14:50", "Трансфер"),
                hotel(3, "2026-12-10T15:00", "2026-12-13T11:00", false),
                seg(4, SegType.EXCURSION, "2026-12-11T10:00", "2026-12-11T14:00", "Экскурсия"),
                seg(5, SegType.TRANSFER, "2026-12-13T12:00", "2026-12-13T12:50", "Трансфер обратно"),
                seg(6, SegType.FLIGHT, "2026-12-13T16:00", "2026-12-13T19:00", "Рейс обратно"));
        assertEquals(0L, r.errors());
        assertEquals(4, r.links().size());
    }
    @Test void zeroSlackIsReportedAsCriticalLink() {
        Result r = run(seg(1, SegType.FLIGHT, "2026-12-10T10:00", "2026-12-10T13:00", "F"), seg(2, SegType.TRANSFER, "2026-12-10T13:45", "2026-12-10T14:30", "T"));
        assertEquals(0L, r.links().get(0).slackMin());
    }
}
