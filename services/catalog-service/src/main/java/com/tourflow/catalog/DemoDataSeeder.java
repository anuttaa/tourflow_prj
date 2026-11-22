package com.tourflow.catalog;

import com.tourflow.catalog.model.*;
import com.tourflow.catalog.repository.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Демо-каталог: популярные направления, поставщики и опции с календарем квот. */
@Component
public class DemoDataSeeder implements CommandLineRunner {
    private static final ZoneId MINSK = ZoneId.of("Europe/Minsk");
    private final DestinationRepository destinations;
    private final SupplierRepository suppliers;
    private final OfferRepository offers;
    private final ScheduleRepository schedules;
    private final PeriodRepository periods;
    private final AvailabilityRepository availability;
    private final RatingRepository ratings;

    @Value("${app.seed-enabled}")
    boolean enabled;

    public DemoDataSeeder(DestinationRepository destinations, SupplierRepository suppliers, OfferRepository offers,
                          ScheduleRepository schedules, PeriodRepository periods,
                          AvailabilityRepository availability, RatingRepository ratings) {
        this.destinations = destinations;
        this.suppliers = suppliers;
        this.offers = offers;
        this.schedules = schedules;
        this.periods = periods;
        this.availability = availability;
        this.ratings = ratings;
    }

    record City(String name, String country, String tz, double lat, double lon, double aLat, double aLon, String iata, int flightMin) {}

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled || destinations.count() > 0) return;
        Supplier air = supplier("SkyBridge Air", "sales@skybridge.example");
        Supplier hot = supplier("Coastline Hotels", "booking@coastline.example");
        Supplier tour = supplier("CityWalk Tours", "info@citywalk.example");
        LocalDate base = LocalDate.now().plusDays(21);

        List<City> cities = List.of(
                new City("Прага", "Чехия", "Europe/Prague", 50.0755, 14.4378, 50.1008, 14.2600, "PRG", 150),
                new City("Тоскана", "Италия", "Europe/Rome", 43.7696, 11.2558, 43.8100, 11.2051, "FLR", 190),
                new City("Доломиты", "Италия", "Europe/Rome", 46.4983, 11.3548, 46.4608, 11.3256, "BZO", 180),
                new City("Стамбул", "Турция", "Europe/Istanbul", 41.0082, 28.9784, 41.2753, 28.7519, "IST", 200),
                new City("Тбилиси", "Грузия", "Asia/Tbilisi", 41.6938, 44.8015, 41.6692, 44.9547, "TBS", 190),
                new City("Барселона", "Испания", "Europe/Madrid", 41.3874, 2.1686, 41.2974, 2.0833, "BCN", 270)
        );

        int n = 0;
        for (City c : cities) {
            Destination d = new Destination();
            d.setName(c.name());
            d.setCountry(c.country());
            d.setTz(c.tz());
            d.setLat(c.lat());
            d.setLon(c.lon());
            d = destinations.save(d);

            int[] out = {390, 555, 820}, back = {600, 870, 1140};
            for (int i = 0; i < 3; i++) {
                flight(air, d, c, true, base, out[i], "SB" + (100 + n * 10 + i), 120 + i * 25 - (i == 1 ? 15 : 0), 4.0 + i * 0.3);
                flight(air, d, c, false, base.plusDays(5), back[i], "SB" + (200 + n * 10 + i), 115 + i * 20, 4.2 + i * 0.2);
            }

            String[] hn = {c.name() + " Budget Inn", c.name() + " Central Hotel", c.name() + " Grand Palace"};
            int[] hc = {3, 4, 5};
            double[] hp = {35, 65, 110}, hr = {3.8, 4.4, 4.8};
            for (int i = 0; i < 3; i++) {
                Offer h = offer(hot, d, "HOTEL", hn[i], hc[i], hp[i], 30, d.getTz(), c.lat() + 0.004 * i, c.lon() + 0.004 * i, c.lat() + 0.004 * i, c.lon() + 0.004 * i, hr[i]);
                h.setPlaceFrom(hn[i] + ", " + c.name());
                h.setPlaceTo(h.getPlaceFrom());
                h.setCheckInFrom(i == 2 ? 900 : 840);
                h.setCheckOutUntil(720);
                offers.save(h);
                seedAvailability(h, base.minusDays(2), base.plusDays(20), 30);
                if (i == 2) period(h, base, base.plusDays(5), "1.150");
                rate(h);
            }

            Object[][] ex = getExcursionsForCity(c.name());
            for (int i = 0; i < ex.length; i++) {
                String title = c.name() + ": " + ex[i][0];
                Offer e = offer(tour, d, "EXCURSION", title, (int) ex[i][3], ((Number) ex[i][1]).doubleValue(), 20, d.getTz(),
                        c.lat() + 0.01 * (i + 1), c.lon() - 0.01 * i, c.lat() + 0.01 * (i + 1), c.lon() - 0.01 * i, (double) ex[i][2]);
                e.setPlaceFrom(title);
                e.setPlaceTo(title);
                e.setAttrs("{\"durationMin\":" + ex[i][7] + "}");
                offers.save(e);
                for (int day : (int[]) ex[i][6]) schedule(e, day, (int) ex[i][4], (int) ex[i][5]);
                seedAvailability(e, base.minusDays(2), base.plusDays(20), 20);
                rate(e);
            }

            String[][] tr = {
                    {"Трансфер аэропорт → отель (групповой)", "12", "2", "4.0", "A"},
                    {"Трансфер аэропорт → отель (индивидуальный)", "28", "4", "4.6", "A"},
                    {"Трансфер отель → аэропорт (групповой)", "12", "2", "4.1", "B"},
                    {"Трансфер отель → аэропорт (индивидуальный)", "28", "4", "4.5", "B"}
            };
            for (String[] t : tr) {
                boolean toHotel = t[4].equals("A");
                Offer o = offer(tour, d, "TRANSFER", c.name() + ": " + t[0], Integer.parseInt(t[2]), Double.parseDouble(t[1]), 50, d.getTz(),
                        toHotel ? c.aLat() : c.lat(), toHotel ? c.aLon() : c.lon(), toHotel ? c.lat() : c.aLat(), toHotel ? c.lon() : c.aLon(), Double.parseDouble(t[3]));
                o.setPlaceFrom(toHotel ? "Аэропорт " + c.iata() : c.name() + ", центр");
                o.setPlaceTo(toHotel ? c.name() + ", центр" : "Аэропорт " + c.iata());
                o.setAttrs("{\"durationMin\":50}");
                offers.save(o);
                seedAvailability(o, base.minusDays(2), base.plusDays(20), 50);
                rate(o);
            }

            Offer ins = offer(air, d, "INSURANCE", c.name() + ": страховка путешественника", 3, 15, 200, d.getTz(), null, null, null, null, 4.5);
            offers.save(ins);
            seedAvailability(ins, base.minusDays(2), base.plusDays(20), 200);
            rate(ins);

            Offer meal = offer(hot, d, "MEAL", c.name() + ": питание, полупансион", 3, 40, 100, d.getTz(), c.lat(), c.lon(), c.lat(), c.lon(), 4.2);
            offers.save(meal);
            seedAvailability(meal, base.minusDays(2), base.plusDays(20), 100);
            rate(meal);

            n++;
        }
    }

    private Object[][] getExcursionsForCity(String cityName) {
        if ("Прага".equals(cityName)) {
            return new Object[][]{
                    {"Обзорная экскурсия по Старому городу", 25, 4.8, 3, 540, 1020, new int[]{1, 2, 3, 4, 5, 6, 7}, 180},
                    {"Пражский Град и Мала-Страна", 30, 4.7, 4, 600, 1080, new int[]{2, 3, 4, 5, 6, 7}, 240},
                    {"Поездка в замок Карлштейн и Кутна-Гору", 55, 4.9, 4, 510, 1200, new int[]{1, 3, 5, 6}, 360},
                    {"Вечерняя прогулка по Влтаве с ужином", 45, 4.8, 4, 1080, 1380, new int[]{1, 2, 3, 4, 5, 6, 7}, 180}
            };
        } else if ("Тоскана".equals(cityName)) {
            return new Object[][]{
                    {"Шедевры Флоренции и галерея Уффици", 35, 4.9, 4, 540, 1020, new int[]{1, 2, 3, 4, 5, 6, 7}, 210},
                    {"Сиена и средневековые площади", 45, 4.8, 4, 600, 1080, new int[]{2, 3, 4, 5, 6, 7}, 270},
                    {"Сан-Джиминьяно и дегустация в Кьянти", 50, 4.9, 4, 570, 1140, new int[]{1, 3, 5, 6}, 300},
                    {"Пейзажи и виллы Валь-д’Орча", 60, 4.9, 5, 540, 1200, new int[]{2, 4, 6, 7}, 360}
            };
        } else if ("Доломиты".equals(cityName)) {
            return new Object[][]{
                    {"Панорамы Валь-Гардены с гидом", 40, 4.8, 4, 540, 1020, new int[]{1, 2, 3, 4, 5, 6, 7}, 240},
                    {"Тре-Чиме: кольцевой горный маршрут", 55, 5.0, 5, 480, 1140, new int[]{1, 3, 5, 6}, 360},
                    {"Озеро Брайес и альпийские тропы", 45, 4.9, 4, 540, 1080, new int[]{2, 4, 6, 7}, 300},
                    {"Альпийские деревни и местная кухня", 35, 4.7, 3, 600, 1200, new int[]{1, 2, 3, 4, 5, 6, 7}, 210}
            };
        }
        return new Object[][]{
                {"Обзорная экскурсия по городу", 20, 4.6, 3, 540, 1020, new int[]{1, 2, 3, 4, 5, 6, 7}, 240},
                {"Музей и старый квартал", 15, 4.2, 2, 600, 1080, new int[]{2, 3, 4, 5, 6, 7}, 180},
                {"Вечерняя прогулка с ужином", 35, 4.7, 4, 1080, 1380, new int[]{1, 2, 3, 4, 5, 6, 7}, 180},
                {"Поездка за город", 40, 4.4, 3, 480, 1140, new int[]{1, 2, 3, 4, 5, 6}, 360}
        };
    }

    private Supplier supplier(String name, String contact) {
        Supplier s = new Supplier();
        s.setName(name);
        s.setContact(contact);
        return suppliers.save(s);
    }

    private Offer offer(Supplier s, Destination d, String type, String title, int cls, double price, int cap, String tz,
                        Double fLat, Double fLon, Double tLat, Double tLon, double rating) {
        Offer o = new Offer();
        o.setSupplierId(s.getId());
        o.setDestinationId(d.getId());
        o.setType(type);
        o.setTitle(title);
        o.setClassLevel(cls);
        o.setBasePrice(BigDecimal.valueOf(price));
        o.setCapacity(cap);
        o.setTz(tz);
        o.setFromLat(fLat);
        o.setFromLon(fLon);
        o.setToLat(tLat);
        o.setToLon(tLon);
        o.setRatingAvg(BigDecimal.valueOf(rating).setScale(2, java.math.RoundingMode.HALF_UP));
        return offers.save(o);
    }

    private void flight(Supplier s, Destination d, City c, boolean outbound, LocalDate date, int depMin, String code, double price, double rating) {
        ZoneId dep = outbound ? MINSK : ZoneId.of(d.getTz());
        ZonedDateTime start = date.atStartOfDay(dep).plusMinutes(depMin);
        String title = outbound ? "Рейс " + code + " Минск → " + c.name() : "Рейс " + code + " " + c.name() + " → Минск";
        Offer o = offer(s, d, "FLIGHT", title, 3, price, 160, dep.getId(),
                outbound ? 53.8825 : c.aLat(), outbound ? 28.0307 : c.aLon(), outbound ? c.aLat() : 53.8825, outbound ? c.aLon() : 28.0307, rating);
        o.setStartUtc(start.toInstant());
        o.setEndUtc(start.plusMinutes(c.flightMin()).toInstant());
        o.setPlaceFrom(outbound ? "Аэропорт Минск MSQ" : "Аэропорт " + c.iata());
        o.setPlaceTo(outbound ? "Аэропорт " + c.iata() : "Аэропорт Минск MSQ");
        o.setInternational(true);
        offers.save(o);
        seedAvailability(o, start.toLocalDate(), start.toLocalDate(), 60);
        rate(o);
    }

    private void seedAvailability(Offer o, LocalDate from, LocalDate to, int quota) {
        List<Availability> list = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            Availability a = new Availability();
            a.setOfferId(o.getId());
            a.setDate(d);
            a.setQuotaTotal(quota);
            a.setQuotaFree(quota);
            list.add(a);
        }
        availability.saveAll(list);
    }

    private void schedule(Offer o, int day, int from, int to) {
        OfferSchedule s = new OfferSchedule();
        s.setOfferId(o.getId());
        s.setDayOfWeek(day);
        s.setOpenFrom(from);
        s.setOpenTo(to);
        schedules.save(s);
    }

    private void period(Offer o, LocalDate from, LocalDate to, String k) {
        PricePeriod p = new PricePeriod();
        p.setOfferId(o.getId());
        p.setDateFrom(from);
        p.setDateTo(to);
        p.setCoefficient(new BigDecimal(k));
        periods.save(p);
    }

    private void rate(Offer o) {
        int r = (int) Math.round(o.getRatingAvg().doubleValue());
        int[] scores = {Math.min(5, r + 1), r, Math.max(1, r - 1)};
        for (int i = 0; i < scores.length; i++) {
            Rating x = new Rating();
            x.setOfferId(o.getId());
            x.setUserRef("seed-" + i);
            x.setScore(Math.max(1, Math.min(5, scores[i])));
            ratings.save(x);
        }
    }
}
