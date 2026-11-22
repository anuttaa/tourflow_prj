package com.tourflow.planning;

import com.tourflow.common.dto.Contracts.*;
import com.tourflow.common.web.ApiException;
import com.tourflow.planning.Dtos.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Демо-туры на данных каталога: Прага, Тоскана, Доломиты, а также тур с намеренными конфликтами для проверки конструктора. */
@Service
public class DemoTourService {
    private static final Logger log = LoggerFactory.getLogger(DemoTourService.class);

    private final TourService tours;
    private final CatalogClient catalog;

    public DemoTourService(TourService tours, CatalogClient catalog) {
        this.tours = tours;
        this.catalog = catalog;
    }

    public List<TourSummary> create() {
        List<DestinationDto> ds = catalog.destinations();
        if (ds.isEmpty()) throw ApiException.conflict("Каталог пуст: нет направлений для демо");

        List<TourSummary> result = new ArrayList<>();
        LocalDate base = LocalDate.now().plusDays(21);

        // 1. Популярные направления из витрины
        for (String cityName : List.of("Прага", "Тоскана", "Доломиты")) {
            DestinationDto d = ds.stream().filter(x -> x.name().equalsIgnoreCase(cityName)).findFirst().orElse(null);
            if (d != null) {
                try {
                    String title = switch (cityName) {
                        case "Прага" -> "Прага: город ста башен";
                        case "Тоскана" -> "Тоскана: искусство и холмы";
                        case "Доломиты" -> "Доломиты: тропы и альпийские долины";
                        default -> cityName + ": классический тур";
                    };
                    int days = switch (cityName) {
                        case "Прага" -> 5;
                        case "Тоскана" -> 7;
                        case "Доломиты" -> 6;
                        default -> 4;
                    };
                    BigDecimal budget = switch (cityName) {
                        case "Прага" -> new BigDecimal("1380");
                        case "Тоскана" -> new BigDecimal("2360");
                        case "Доломиты" -> new BigDecimal("1880");
                        default -> new BigDecimal("1500");
                    };
                    TourSummary ts = build(d, base, title, false, days, budget);
                    // Публикуем готовый тур на витрину
                    try {
                        ts = tours.publish(ts.id());
                    } catch (Exception pe) {
                        log.warn("Не удалось автоматически опубликовать тур {}: {}", title, pe.getMessage());
                    }
                    result.add(ts);
                } catch (Exception e) {
                    log.error("Ошибка при создании демо-тура для {}: {}", cityName, e.getMessage());
                }
            }
        }

        // Если ни один из них не создался, используем первое доступное направление
        if (result.isEmpty()) {
            DestinationDto d = ds.get(0);
            TourSummary ts = build(d, base, d.name() + ": корректный тур (демо)", false, 4, new BigDecimal("1500"));
            try {
                ts = tours.publish(ts.id());
            } catch (Exception ignored) {}
            result.add(ts);
        }

        // Тур с намеренными конфликтами для тестирования менеджером в конструкторе
        DestinationDto dConflict = ds.stream().filter(x -> x.name().equalsIgnoreCase("Прага")).findFirst().orElse(ds.get(0));
        try {
            result.add(build(dConflict, base, dConflict.name() + ": черновик с конфликтами (демо)", true, 4, new BigDecimal("1200")));
        } catch (Exception ignored) {}

        return result;
    }

    private List<OfferSnapshot> find(DestinationDto d, String type, Instant ws, Instant we, List<LocalDate> dates) {
        List<OfferSnapshot> l = new ArrayList<>(catalog.candidates(new CandidateRequest(List.of(type), d.id(), ws, we, 2, dates, null, 100))
                .stream().map(CheckResult::offer).toList());
        l.sort(Comparator.comparing((OfferSnapshot o) -> o.startUtc() == null ? Instant.EPOCH : o.startUtc()).thenComparing(OfferSnapshot::basePrice));
        if (l.isEmpty()) throw ApiException.conflict("В каталоге нет опций типа " + type + " для демо в направлении " + d.name());
        return l;
    }

    private OfferSnapshot named(List<OfferSnapshot> l, String part) {
        return l.stream().filter(o -> o.title().toLowerCase().contains(part.toLowerCase())).findFirst().orElse(l.get(0));
    }

    private TourSummary build(DestinationDto d, LocalDate base, String title, boolean broken, int durationDays, BigDecimal budget) {
        ZoneId z = ZoneId.of(d.tz());
        LocalDate end = base.plusDays(durationDays - 1);
        TourSummary t = tours.create(new TourForm(title, d.id(), base, end, 2, budget, "BALANCE"));
        long id = t.id();
        Instant lo = base.minusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant hi = base.plusDays(2).atStartOfDay(ZoneOffset.UTC).toInstant();

        // 1. Вылет туда
        List<OfferSnapshot> flights = find(d, "FLIGHT", lo, hi, List.of());
        OfferSnapshot out = flights.get(broken ? 0 : Math.min(1, flights.size() - 1));
        tours.addSegment(id, new SegmentForm(out.offerId(), null, null, false));
        Instant arrival = out.endUtc() != null ? out.endUtc() : base.atTime(12, 0).atZone(z).toInstant();

        // 2. Трансфер в отель
        List<OfferSnapshot> transfersIn = find(d, "TRANSFER", lo, hi, List.of(base));
        OfferSnapshot trIn = named(transfersIn, "аэропорт → отель");
        Instant trStart = arrival.plus(Duration.ofMinutes(broken ? 30 : 60));
        tours.addSegment(id, new SegmentForm(trIn.offerId(), trStart, trStart.plus(Duration.ofMinutes(50)), false));

        // 3. Отель
        List<LocalDate> nights = new ArrayList<>();
        int nightCount = broken ? Math.min(2, durationDays - 2) : durationDays - 1;
        for (int i = 0; i < nightCount; i++) {
            nights.add(base.plusDays(i));
        }
        List<OfferSnapshot> hotels = find(d, "HOTEL", lo, hi, nights);
        OfferSnapshot hotel = hotels.stream().filter(o -> o.classLevel() == 4).findFirst().orElse(hotels.get(0));
        Instant hin = base.atTime(broken ? 10 : 14, 30).atZone(z).toInstant();
        Instant hout = (broken ? base.plusDays(2) : end).atTime(11, 30).atZone(z).toInstant();
        tours.addSegment(id, new SegmentForm(hotel.offerId(), hin, hout, false));

        // 4. Экскурсии
        LocalDate d1 = base.plusDays(1);
        List<OfferSnapshot> excursions = find(d, "EXCURSION", lo, hi, List.of(d1));
        OfferSnapshot ex1 = excursions.get(0);
        Instant e1s = d1.atTime(broken ? 8 : 10, 0).atZone(z).toInstant();
        tours.addSegment(id, new SegmentForm(ex1.offerId(), e1s, e1s.plus(Duration.ofHours(3)), false));

        if (broken) {
            OfferSnapshot ex2 = excursions.size() > 1 ? excursions.get(1) : ex1;
            Instant s2 = d1.atTime(11, 0).atZone(z).toInstant(); // перекрытие с первой экскурсией
            tours.addSegment(id, new SegmentForm(ex2.offerId(), s2, s2.plus(Duration.ofHours(3)), false));
        } else if (excursions.size() > 1 && durationDays > 2) {
            LocalDate d2 = base.plusDays(2);
            OfferSnapshot ex3 = excursions.get(1);
            Instant s3 = d2.atTime(10, 30).atZone(z).toInstant();
            tours.addSegment(id, new SegmentForm(ex3.offerId(), s3, s3.plus(Duration.ofHours(3)), false));
        }

        // 5. Обратный рейс и трансфер
        Instant rlo = end.minusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant rhi = end.plusDays(2).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<OfferSnapshot> returnFlights = find(d, "FLIGHT", rlo, rhi, List.of());
        OfferSnapshot back = returnFlights.get(broken ? 0 : Math.min(2, returnFlights.size() - 1));

        List<OfferSnapshot> transfersOut = find(d, "TRANSFER", rlo, rhi, List.of(end));
        OfferSnapshot trOut = named(transfersOut, "отель → аэропорт");
        Instant backStart = back.startUtc() != null ? back.startUtc() : end.atTime(18, 0).atZone(z).toInstant();
        Instant ts = backStart.minus(Duration.ofMinutes(broken ? 60 : 190));
        tours.addSegment(id, new SegmentForm(trOut.offerId(), ts, ts.plus(Duration.ofMinutes(50)), false));
        tours.addSegment(id, new SegmentForm(back.offerId(), null, null, false));

        // 6. Страховка
        List<OfferSnapshot> insList = find(d, "INSURANCE", lo, hi, List.of(base));
        if (!insList.isEmpty()) {
            OfferSnapshot ins = insList.get(0);
            tours.addSegment(id, new SegmentForm(ins.offerId(), base.atStartOfDay(z).toInstant(), end.plusDays(1).atStartOfDay(z).toInstant(), false));
        }

        return tours.view(id).tour();
    }
}
