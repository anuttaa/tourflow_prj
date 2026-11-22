package com.tourflow.catalog;

import com.tourflow.catalog.model.*;
import com.tourflow.catalog.repository.*;
import com.tourflow.common.dto.Contracts.*;
import com.tourflow.common.outbox.OutboxService;
import com.tourflow.common.web.ApiException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {
    private final OfferRepository offers; private final ScheduleRepository schedules; private final PeriodRepository periods;
    private final AvailabilityRepository availability; private final HoldRepository holds; private final OutboxService outbox;

    public CatalogService(OfferRepository offers, ScheduleRepository schedules, PeriodRepository periods, AvailabilityRepository availability, HoldRepository holds, OutboxService outbox) {
        this.offers = offers; this.schedules = schedules; this.periods = periods; this.availability = availability; this.holds = holds; this.outbox = outbox;
    }

    // ---------- СЃРЅРёРјРєРё Рё РїСЂРѕРІРµСЂРєР° РґРѕСЃС‚СѓРїРЅРѕСЃС‚Рё ----------

    private static LocalDate startDate(Offer o) { return o.getStartUtc().atZone(ZoneId.of(o.getTz())).toLocalDate(); }

    BigDecimal seasonK(Offer o, LocalDate d) {
        if (d == null) return BigDecimal.ONE;
        return periods.findByOfferId(o.getId()).stream().filter(p -> !d.isBefore(p.getDateFrom()) && !d.isAfter(p.getDateTo()))
                .map(PricePeriod::getCoefficient).findFirst().orElse(BigDecimal.ONE);
    }

    OfferSnapshot snapshot(Offer o, LocalDate firstDate) {
        List<OfferSchedule> sch = schedules.findByOfferId(o.getId());
        Integer from = sch.stream().map(OfferSchedule::getOpenFrom).min(Integer::compare).orElse(null);
        Integer to = sch.stream().map(OfferSchedule::getOpenTo).max(Integer::compare).orElse(null);
        List<Integer> days = sch.stream().map(OfferSchedule::getDayOfWeek).sorted().toList();
        return new OfferSnapshot(o.getId(), o.getType(), o.getTitle(), o.getDestinationId(), o.getClassLevel(), o.getRatingAvg().doubleValue(),
                o.getBasePrice(), seasonK(o, firstDate), o.getCapacity(), o.getStartUtc(), o.getEndUtc(), o.getTz(), o.getPlaceFrom(), o.getPlaceTo(),
                o.getFromLat(), o.getFromLon(), o.getToLat(), o.getToLon(), o.isInternational(), o.getCheckInFrom(), o.getCheckOutUntil(), from, to, days);
    }

    private int minFree(long offerId, List<LocalDate> dates) {
        if (dates.isEmpty()) return 0;
        Map<LocalDate, Integer> m = availability.findByOfferIdAndDateIn(offerId, dates).stream()
                .collect(Collectors.toMap(Availability::getDate, Availability::getQuotaFree));
        int min = Integer.MAX_VALUE;
        for (LocalDate d : dates) min = Math.min(min, m.getOrDefault(d, 0));
        return min;
    }

    private List<LocalDate> datesFor(Offer o, List<LocalDate> requested) {
        if (requested != null && !requested.isEmpty()) return requested;
        return o.getStartUtc() != null ? List.of(startDate(o)) : List.of();
    }

    @Transactional(readOnly = true)
    public CheckResult check(CheckItem item) {
        Offer o = offers.findById(item.offerId()).filter(Offer::isActive).orElse(null);
        if (o == null) return new CheckResult(item.offerId(), null, 0, false);
        List<LocalDate> dates = datesFor(o, item.dates());
        int free = dates.isEmpty() ? o.getCapacity() : minFree(o.getId(), dates);
        return new CheckResult(o.getId(), snapshot(o, dates.isEmpty() ? null : dates.get(0)), free, free >= Math.max(item.qty(), 1));
    }

    /** РљР°РЅРґРёРґР°С‚С‹ РґР»СЏ Р°РІС‚РѕРїРѕРґР±РѕСЂР° (Р°Р»РіРѕСЂРёС‚Рј 2, С€Р°Рі 1): С‚РѕС‚ Р¶Рµ С‚РёРї РёР»Рё СЃРѕРІРјРµСЃС‚РёРјС‹Р№, РѕРєРЅРѕ РІСЂРµРјРµРЅРё, РѕСЃС‚Р°С‚РѕРє РєРІРѕС‚С‹ РЅРµ РјРµРЅСЊС€Рµ pax. */
    @Transactional(readOnly = true)
    public List<CheckResult> candidates(CandidateRequest r) {
        List<CheckResult> out = new ArrayList<>();
        int limit = r.maxResults() > 0 ? Math.min(r.maxResults(), 100) : 30;
        for (Offer o : offers.findByDestinationIdAndTypeInAndActiveTrue(r.destinationId(), r.types())) {
            if (r.excludeOfferId() != null && r.excludeOfferId().equals(o.getId())) continue;
            List<LocalDate> dates;
            if (o.getStartUtc() != null) {
                Instant end = o.getEndUtc() != null ? o.getEndUtc() : o.getStartUtc();
                if (o.getStartUtc().isBefore(r.windowStart()) || end.isAfter(r.windowEnd())) continue;
                dates = List.of(startDate(o));
            } else dates = r.dates() == null ? List.of() : r.dates();
            if (dates.isEmpty()) continue;
            int free = minFree(o.getId(), dates);
            if (free < r.pax()) continue;
            out.add(new CheckResult(o.getId(), snapshot(o, dates.get(0)), free, true));
            if (out.size() >= limit) break;
        }
        return out;
    }

    // ---------- СѓРґРµСЂР¶Р°РЅРёРµ РєРІРѕС‚ (Р°Р»РіРѕСЂРёС‚Рј 3, С€Р°РіРё 2вЂ“3) ----------

    /** РРґРµРјРїРѕС‚РµРЅС‚РЅРѕ РїРѕ bookingRef. Р›СЋР±Р°СЏ РЅРµСѓРґР°С‡Р° РѕС‚РєР°С‚С‹РІР°РµС‚ РІСЃСЋ С‚СЂР°РЅР·Р°РєС†РёСЋ вЂ” С‡Р°СЃС‚РёС‡РЅС‹С… СѓРґРµСЂР¶Р°РЅРёР№ РЅРµ РѕСЃС‚Р°С‘С‚СЃСЏ. */
    @Transactional
    public List<Long> hold(HoldRequest req) {
        List<Hold> existing = holds.findByBookingRefAndStatusIn(req.bookingRef(), List.of("ACTIVE", "CONVERTED"));
        if (!existing.isEmpty()) return existing.stream().map(Hold::getId).toList();
        Instant expires = Instant.now().plusSeconds(60L * Math.max(req.ttlMinutes(), 1));
        List<Long> ids = new ArrayList<>();
        for (HoldItem it : req.items()) {
            Offer o = offers.findById(it.offerId()).filter(Offer::isActive)
                    .orElseThrow(() -> ApiException.conflict("РћРїС†РёСЏ " + it.offerId() + " Р±РѕР»СЊС€Рµ РЅРµРґРѕСЃС‚СѓРїРЅР°"));
            for (LocalDate d : datesFor(o, it.dates())) {
                if (availability.decrement(o.getId(), d, it.qty()) == 0)
                    throw ApiException.conflict("РњРµСЃС‚Р° Р·Р°РєРѕРЅС‡РёР»РёСЃСЊ: В«" + o.getTitle() + "В» РЅР° " + d);
                Hold h = new Hold();
                h.setBookingRef(req.bookingRef()); h.setOfferId(o.getId()); h.setDate(d); h.setQty(it.qty());
                h.setStatus("ACTIVE"); h.setExpiresAt(expires);
                ids.add(holds.save(h).getId());
            }
        }
        return ids;
    }

    @Transactional
    public int release(String bookingRef) {
        int n = 0;
        for (Hold h : holds.findByBookingRefAndStatusIn(bookingRef, List.of("ACTIVE", "CONVERTED"))) {
            availability.increment(h.getOfferId(), h.getDate(), h.getQty());
            h.setStatus("RELEASED");
            n++;
        }
        return n;
    }

    @Transactional
    public int convert(String bookingRef) {
        int n = 0;
        for (Hold h : holds.findByBookingRefAndStatusIn(bookingRef, List.of("ACTIVE"))) {
            if (h.getExpiresAt().isBefore(Instant.now())) throw ApiException.conflict("РЎСЂРѕРє СѓРґРµСЂР¶Р°РЅРёСЏ РјРµСЃС‚ РёСЃС‚С‘Рє");
            h.setStatus("CONVERTED");
            n++;
        }
        return n;
    }

    // ---------- СѓРїСЂР°РІР»РµРЅРёРµ РєР°С‚Р°Р»РѕРіРѕРј ----------

    public record ScheduleForm(int dayOfWeek, int openFrom, int openTo) {}
    public record PeriodForm(LocalDate dateFrom, LocalDate dateTo, BigDecimal coefficient) {}

    @Transactional
    public Offer save(Offer o, List<ScheduleForm> sch, List<PeriodForm> per) {
        boolean isNew = o.getId() == null;
        o.setUpdatedAt(Instant.now());
        Offer saved = offers.save(o);
        schedules.deleteByOfferId(saved.getId());
        periods.deleteByOfferId(saved.getId());
        schedules.flush();
        if (sch != null) for (ScheduleForm s : sch) {
            OfferSchedule e = new OfferSchedule();
            e.setOfferId(saved.getId()); e.setDayOfWeek(s.dayOfWeek()); e.setOpenFrom(s.openFrom()); e.setOpenTo(s.openTo());
            schedules.save(e);
        }
        if (per != null) for (PeriodForm p : per) {
            PricePeriod e = new PricePeriod();
            e.setOfferId(saved.getId()); e.setDateFrom(p.dateFrom()); e.setDateTo(p.dateTo()); e.setCoefficient(p.coefficient());
            periods.save(e);
        }
        outbox.publish("offer.updated", String.valueOf(saved.getId()), Map.of("offerId", saved.getId(), "change", isNew ? "CREATED" : "UPDATED"));
        return saved;
    }

    @Transactional
    public void deactivate(long id) {
        Offer o = offers.findById(id).orElseThrow(() -> ApiException.notFound("РћРїС†РёСЏ"));
        o.setActive(false);
        outbox.publish("offer.updated", String.valueOf(id), Map.of("offerId", id, "change", "DELETED"));
    }

    /** FR-07: РјР°СЃСЃРѕРІРѕРµ Р·Р°РґР°РЅРёРµ РєРІРѕС‚ РЅР° РїРµСЂРёРѕРґ; Р·Р°РєСЂС‹С‚РёРµ РґР°С‚ вЂ” quota = 0. */
    @Transactional
    public int setAvailability(long offerId, LocalDate from, LocalDate to, int quota) {
        if (to.isBefore(from)) throw ApiException.invalid("Р”Р°С‚Р° РѕРєРѕРЅС‡Р°РЅРёСЏ СЂР°РЅСЊС€Рµ РґР°С‚С‹ РЅР°С‡Р°Р»Р°");
        if (from.plusDays(400).isBefore(to)) throw ApiException.invalid("РџРµСЂРёРѕРґ РЅРµ Р±РѕР»РµРµ 400 РґРЅРµР№");
        offers.findById(offerId).orElseThrow(() -> ApiException.notFound("РћРїС†РёСЏ"));
        int n = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            Availability a = availability.findByOfferIdAndDate(offerId, d).orElse(null);
            if (a == null) {
                a = new Availability(); a.setOfferId(offerId); a.setDate(d); a.setQuotaTotal(quota); a.setQuotaFree(quota);
            } else {
                int free = Math.max(0, Math.min(quota, a.getQuotaFree() + (quota - a.getQuotaTotal())));
                a.setQuotaTotal(quota); a.setQuotaFree(free); a.setVersion(a.getVersion() + 1);
            }
            availability.save(a);
            n++;
        }
        outbox.publish("availability.changed", String.valueOf(offerId), Map.of("offerId", offerId, "from", from.toString(), "to", to.toString()));
        return n;
    }

    @Transactional(readOnly = true)
    public List<Availability> availability(long offerId, LocalDate from, LocalDate to) {
        return availability.findByOfferIdAndDateBetweenOrderByDate(offerId, from, to);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> details(long id) {
        Offer o = offers.findById(id).orElseThrow(() -> ApiException.notFound("РћРїС†РёСЏ"));
        return Map.of("offer", o, "schedules", schedules.findByOfferId(id), "pricePeriods", periods.findByOfferId(id));
    }
}

