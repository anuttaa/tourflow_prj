package com.tourflow.planning.engine;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Алгоритм 1 (ТЗ 7.2): валидация временного графика тура.
 * Граф: вершины — сегменты переднего плана, ребро a→b с запасом slack = start_b − end_a − gap_min(a,b).
 * Сложность O(n log n) (сортировка) + O(n) проход.
 */
public final class ScheduleValidator {
    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("dd.MM");
    private static final DateTimeFormatter TF = DateTimeFormatter.ofPattern("dd.MM HH:mm");

    private ScheduleValidator() {}

    public record TourParams(LocalDate start, LocalDate end, String tz, int pax, BigDecimal budget, BigDecimal total) {}

    /** Ребро графа: required — требуемый интервал gap_min, slack — запас (может быть отрицательным). */
    public record Link(long fromId, long toId, long requiredMin, long slackMin) {}

    public record Result(List<Conflict> conflicts, List<Link> links) {
        public long errors() { return conflicts.stream().filter(c -> c.severity() == Severity.ERROR).count(); }
        public long warnings() { return conflicts.stream().filter(c -> c.severity() == Severity.WARNING).count(); }
    }

    public static Result validate(TourParams t, List<Seg> segs, Rules r) {
        List<Conflict> out = new ArrayList<>();
        List<Link> links = new ArrayList<>();
        ZoneId tourZone = ZoneId.of(t.tz());
        Map<Long, Seg> byId = new HashMap<>();

        // VR-03: корректность времени и часового пояса
        List<Seg> valid = new ArrayList<>();
        for (Seg s : segs) {
            boolean ok = true;
            try { ZoneId.of(s.tz()); } catch (Exception e) {
                out.add(err("VR-03", s.id(), null, 0, "Неизвестный часовой пояс «" + s.tz() + "» у сегмента «" + s.title() + "»"));
                ok = false;
            }
            if (!s.end().isAfter(s.start())) {
                out.add(err("VR-03", s.id(), null, 0, "Окончание сегмента «" + s.title() + "» должно быть позже начала"));
                ok = false;
            }
            if (ok) { valid.add(s); byId.put(s.id(), s); }
        }

        if (segs.isEmpty()) out.add(err("VR-08", null, null, 0, "Тур пуст: добавьте сегменты (прибытие, проживание, отправление)"));

        List<Seg> fg = valid.stream().filter(s -> !s.type().background())
                .sorted(Comparator.comparing(Seg::start).thenComparing(Seg::end)).toList();

        // VR-01, VR-02, VR-04: граф переднего плана
        Seg frontier = null;
        Seg prev = null;
        for (Seg b : fg) {
            if (frontier != null) {
                Seg a = frontier;
                if (b.start().isBefore(a.end())) {
                    long ov = Duration.between(b.start(), a.end()).toMinutes();
                    out.add(err("VR-01", a.id(), b.id(), ov, "«" + a.title() + "» и «" + b.title() + "» пересекаются на " + ov
                            + " мин. Сдвиньте «" + b.title() + "» позже на " + ov + " мин или выберите другую опцию"));
                } else {
                    long gap = Duration.between(a.end(), b.start()).toMinutes();
                    long buffer = (long) Math.ceil(bufferMin(a, b, r));
                    long travel = travelMin(a, b, r);
                    long required = Math.max(buffer, travel);
                    long slack = gap - required;
                    links.add(new Link(a.id(), b.id(), required, slack));
                    if (slack < 0) {
                        boolean byTravel = travel > buffer;
                        out.add(err(byTravel ? "VR-04" : "VR-02", a.id(), b.id(), -slack,
                                (byTravel ? "Не хватает времени на дорогу между «" : "Мал интервал между «") + a.title() + "» и «" + b.title()
                                        + "»: нужно " + required + " мин, есть " + gap + ". Сдвиньте «" + b.title() + "» на " + (-slack) + " мин позже"));
                    } else if (slack < r.get("WARN_SLACK_RATIO") * required) {
                        out.add(warn("VR-02", a.id(), b.id(), 0, "Запас между «" + a.title() + "» и «" + b.title() + "» всего " + slack + " мин (нужно " + required + ")"));
                    }
                    // VR-10 (перерыв): подряд две экскурсии
                    if (a.type() == SegType.EXCURSION && b.type() == SegType.EXCURSION && gap < r.get("MIN_BREAK_MIN")) {
                        out.add(warn("VR-10", a.id(), b.id(), (long) r.get("MIN_BREAK_MIN") - gap, "Между экскурсиями нет перерыва на отдых (меньше " + (long) r.get("MIN_BREAK_MIN") + " мин)"));
                    }
                }
            }
            if (frontier == null || b.end().isAfter(frontier.end())) frontier = b;
            prev = b;
        }

        // Локальные правила по каждому сегменту
        Instant lower = t.start().atStartOfDay(tourZone).toInstant();
        Instant upper = t.end().plusDays(1).atStartOfDay(tourZone).toInstant();
        for (Seg s : valid) {
            ZoneId z = ZoneId.of(s.tz());
            ZonedDateTime zs = s.start().atZone(z), ze = s.end().atZone(z);

            // VR-05: часы работы и расписание
            if (s.openFrom() != null && s.openTo() != null) {
                if (s.openDays() != null && !s.openDays().isEmpty() && !s.openDays().contains(zs.getDayOfWeek().getValue())) {
                    out.add(err("VR-05", s.id(), null, 0, "«" + s.title() + "» не работает в этот день недели (" + ru(zs.getDayOfWeek()) + ")"));
                } else {
                    ZonedDateTime ws = zs.toLocalDate().atStartOfDay(z).plusMinutes(s.openFrom());
                    ZonedDateTime we = zs.toLocalDate().atStartOfDay(z).plusMinutes(s.openTo());
                    if (zs.isBefore(ws)) {
                        long v = Duration.between(zs, ws).toMinutes();
                        out.add(err("VR-05", s.id(), null, v, "«" + s.title() + "» начинается раньше открытия на " + v + " мин. Сдвиньте на " + v + " мин позже"));
                    } else if (ze.isAfter(we)) {
                        long v = Duration.between(we, ze).toMinutes();
                        out.add(err("VR-05", s.id(), null, v, "«" + s.title() + "» заканчивается после закрытия на " + v + " мин. Сдвиньте на " + v + " мин раньше"));
                    }
                }
            }

            // VR-06: заселение и выселение
            if (s.type() == SegType.HOTEL) {
                int cin = s.checkInFrom() != null ? s.checkInFrom() : (int) r.get("CHECKIN_FROM");
                int cout = s.checkOutUntil() != null ? s.checkOutUntil() : (int) r.get("CHECKOUT_UNTIL");
                ZonedDateTime allowedIn = zs.toLocalDate().atStartOfDay(z).plusMinutes(cin);
                if (zs.isBefore(allowedIn)) {
                    long v = Duration.between(zs, allowedIn).toMinutes();
                    String msg = "Заселение в «" + s.title() + "» раньше разрешённого на " + v + " мин (с " + hm(cin) + ")";
                    out.add(s.earlyCheckIn() ? warn("VR-06", s.id(), null, v, msg + ", ранний заезд по запросу") : err("VR-06", s.id(), null, v, msg + ". Сдвиньте заселение или запросите ранний заезд"));
                }
                ZonedDateTime allowedOut = ze.toLocalDate().atStartOfDay(z).plusMinutes(cout);
                if (ze.isAfter(allowedOut)) {
                    long v = Duration.between(allowedOut, ze).toMinutes();
                    out.add(err("VR-06", s.id(), null, v, "Выселение из «" + s.title() + "» позже разрешённого на " + v + " мин (до " + hm(cout) + ")"));
                }
            }

            // VR-08: границы тура
            if (s.start().isBefore(lower) || s.end().isAfter(upper)) {
                long v = s.start().isBefore(lower) ? Duration.between(s.start(), lower).toMinutes() : Duration.between(upper, s.end()).toMinutes();
                out.add(err("VR-08", s.id(), null, v, "«" + s.title() + "» выходит за даты тура на " + v + " мин"));
            }

            // VR-09: вместимость и доступность
            if (s.capacity() != null && t.pax() > s.capacity()) {
                out.add(err("VR-09", s.id(), null, t.pax() - s.capacity(), "«" + s.title() + "»: вместимость " + s.capacity() + ", участников " + t.pax()));
            }
            if (s.free() == null) {
                out.add(warn("VR-09", s.id(), null, 0, "Актуальность доступности «" + s.title() + "» не проверена"));
            } else if (s.free() < t.pax()) {
                out.add(err("VR-09", s.id(), null, t.pax() - s.free(), "«" + s.title() + "»: свободно мест " + s.free() + ", нужно " + t.pax() + ". Подберите замену"));
            } else if (s.stale()) {
                out.add(warn("VR-09", s.id(), null, 0, "Данные каталога по «" + s.title() + "» изменились, обновите проверку"));
            }
        }

        // VR-08: прибытие и отправление
        if (!fg.isEmpty()) {
            if (!fg.get(0).type().transport())
                out.add(err("VR-08", fg.get(0).id(), null, 0, "Первый сегмент маршрута должен быть прибытием (рейс, поезд, автобус)"));
            if (!fg.get(fg.size() - 1).type().transport())
                out.add(err("VR-08", fg.get(fg.size() - 1).id(), null, 0, "Последний сегмент маршрута должен быть отправлением (рейс, поезд, автобус)"));
        }

        // VR-07: покрытие ночей
        for (LocalDate d = t.start(); d.isBefore(t.end()); d = d.plusDays(1)) {
            boolean covered = false;
            Instant probe = d.plusDays(1).atTime(2, 0).atZone(tourZone).toInstant();
            for (Seg s : valid) {
                if (s.type() == SegType.HOTEL) {
                    ZoneId z = ZoneId.of(s.tz());
                    LocalDate sd = s.start().atZone(z).toLocalDate(), ed = s.end().atZone(z).toLocalDate();
                    if (!sd.isAfter(d) && ed.isAfter(d)) { covered = true; break; }
                } else if (s.type().transport() && !s.start().isAfter(probe) && !s.end().isBefore(probe)) { covered = true; break; }
            }
            if (!covered) out.add(err("VR-07", null, null, 0, "Не покрыта ночь с " + d.format(DF) + " на " + d.plusDays(1).format(DF) + ": добавьте проживание"));
        }

        // VR-10: суточная нагрузка (активные сегменты, кроме питания)
        Map<LocalDate, Long> load = new HashMap<>();
        for (Seg s : fg) {
            if (s.type() == SegType.MEAL) continue;
            ZonedDateTime cur = s.start().atZone(tourZone);
            ZonedDateTime endZ = s.end().atZone(tourZone);
            while (cur.isBefore(endZ)) {
                ZonedDateTime next = cur.toLocalDate().plusDays(1).atStartOfDay(tourZone);
                ZonedDateTime chunkEnd = next.isBefore(endZ) ? next : endZ;
                load.merge(cur.toLocalDate(), Duration.between(cur, chunkEnd).toMinutes(), Long::sum);
                cur = chunkEnd;
            }
        }
        long maxLoad = (long) r.get("MAX_DAILY_ACTIVE_MIN");
        load.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
            if (e.getValue() > maxLoad)
                out.add(warn("VR-10", null, null, e.getValue() - maxLoad, "Нагрузка " + e.getKey().format(DF) + ": " + e.getValue() / 60 + " ч " + e.getValue() % 60 + " мин при норме " + maxLoad / 60 + " ч"));
        });

        // BR-01: бюджет (ТЗ 7.4)
        if (t.budget() != null && t.total() != null && t.budget().signum() > 0 && t.total().compareTo(t.budget()) > 0) {
            BigDecimal limit = t.budget().multiply(BigDecimal.ONE.add(BigDecimal.valueOf(r.get("BUDGET_TOLERANCE"))));
            String msg = "Стоимость " + t.total() + " превышает бюджет " + t.budget();
            out.add(t.total().compareTo(limit) > 0 ? err("BR-01", null, null, 0, msg + " более чем на 10%") : warn("BR-01", null, null, 0, msg));
        }

        out.sort(Comparator.comparing((Conflict c) -> c.severity() == Severity.ERROR ? 0 : 1).thenComparing(Conflict::rule));
        return new Result(out, links);
    }

    static double bufferMin(Seg a, Seg b, Rules r) {
        if (a.type() == SegType.FLIGHT && b.type() == SegType.FLIGHT)
            return (a.international() || b.international()) ? r.get("BUF_FLIGHT_FLIGHT_INTL") : r.get("BUF_FLIGHT_FLIGHT_DOM");
        if (b.type() == SegType.FLIGHT) return r.get("BUF_ANY_FLIGHT");
        if (a.type() == SegType.FLIGHT) return r.get("BUF_FLIGHT_ANY");
        if (a.type() == SegType.TRAIN || a.type() == SegType.BUS || b.type() == SegType.TRAIN || b.type() == SegType.BUS) return r.get("BUF_STATION_ANY");
        if (a.type() == SegType.TRANSFER || b.type() == SegType.TRANSFER) return r.get("BUF_TRANSFER_ANY");
        return r.get("BUF_DEFAULT");
    }

    static long travelMin(Seg a, Seg b, Rules r) {
        double km = Geo.between(a, b);
        if (km < 0.2) return 0;
        return (long) Math.ceil(km / r.get("SPEED_KMH") * 60.0);
    }

    private static Conflict err(String rule, Long a, Long b, long v, String hint) { return new Conflict(rule, Severity.ERROR, a, b, v, hint); }
    private static Conflict warn(String rule, Long a, Long b, long v, String hint) { return new Conflict(rule, Severity.WARNING, a, b, v, hint); }
    private static String hm(int minutes) { return String.format("%02d:%02d", minutes / 60, minutes % 60); }
    private static String ru(DayOfWeek d) {
        return switch (d) { case MONDAY -> "пн"; case TUESDAY -> "вт"; case WEDNESDAY -> "ср"; case THURSDAY -> "чт"; case FRIDAY -> "пт"; case SATURDAY -> "сб"; case SUNDAY -> "вс"; };
    }
}
