package com.tourflow.planning;

import com.tourflow.common.dto.Contracts.*;
import com.tourflow.common.outbox.OutboxService;
import com.tourflow.common.pricing.PriceCalculator;
import com.tourflow.common.web.ApiException;
import com.tourflow.common.web.CurrentUser;
import com.tourflow.planning.Dtos.*;
import com.tourflow.planning.model.*;
import com.tourflow.planning.repository.*;
import com.tourflow.planning.engine.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TourService {
    final TourRepository tours; final TourVersionRepository versions; final TourSegmentRepository segments; final ValidationRunRepository runs; final ValidationConflictRepository conflicts; final RuleParamRepository RuleParamRepository;
    final CatalogClient catalog; final IdentityClient identity; final OutboxService outbox; final JdbcTemplate jdbc; final BigDecimal margin;

    public TourService(TourRepository tours, TourVersionRepository versions, TourSegmentRepository segments, ValidationRunRepository runs, ValidationConflictRepository conflicts, RuleParamRepository RuleParamRepository,
                       CatalogClient catalog, IdentityClient identity, OutboxService outbox, JdbcTemplate jdbc,
                       @Value("${app.pricing.margin}") BigDecimal margin) {
        this.tours = tours; this.versions = versions; this.segments = segments; this.runs = runs; this.conflicts = conflicts; this.RuleParamRepository = RuleParamRepository;
        this.catalog = catalog; this.identity = identity; this.outbox = outbox; this.jdbc = jdbc; this.margin = margin;
    }

    // ---------- Р·Р°РіСЂСѓР·РєР° Рё РїСЂРµРѕР±СЂР°Р·РѕРІР°РЅРёСЏ ----------

    Tour tour(long id) { return tours.findById(id).orElseThrow(() -> ApiException.notFound("Тур")); }
    TourVersion current(Tour t) { return versions.findById(t.getCurrentVersionId()).orElseThrow(() -> ApiException.notFound("Версия тура")); }

    void ensureEditable(Tour t) {
        if (t.getStatus().equals("PUBLISHED") || t.getStatus().equals("ARCHIVED"))
            throw ApiException.conflict("Тур опубликован: создайте новую версию, чтобы редактировать");
    }

    static Set<Integer> days(String csv) {
        if (csv == null || csv.isBlank()) return Set.of();
        return Arrays.stream(csv.split(",")).map(String::trim).filter(x -> !x.isEmpty()).map(Integer::parseInt).collect(Collectors.toSet());
    }

    static Seg toSeg(TourSegment s) {
        return new Seg(s.getId(), SegType.of(s.getType()), s.getStartUtc(), s.getEndUtc(), s.getTz(), s.getFromLat(), s.getFromLon(), s.getToLat(), s.getToLon(),
                s.isInternational(), s.getOpenFrom(), s.getOpenTo(), days(s.getOpenDays()), s.getCheckInFrom(), s.getCheckOutUntil(), s.isEarlyCheckIn(),
                s.getTitle(), s.getCapacity(), s.getFreeSnapshot(), s.isStale());
    }

    /** Р”Р°С‚С‹ РґР»СЏ РїСЂРѕРІРµСЂРєРё РєРІРѕС‚: РґР»СЏ РїСЂРѕР¶РёРІР°РЅРёСЏ вЂ” РЅРѕС‡Рё, РґР»СЏ РѕСЃС‚Р°Р»СЊРЅС‹С… вЂ” РґР°С‚Р° РЅР°С‡Р°Р»Р° РІ Р·РѕРЅРµ СЃРµРіРјРµРЅС‚Р°. */
    static List<LocalDate> datesOf(TourSegment s) { return datesOf(s.getType(), s.getStartUtc(), s.getEndUtc(), s.getTz()); }

    static List<LocalDate> datesOf(String type, Instant start, Instant end, String tz) {
        ZoneId z = ZoneId.of(tz);
        LocalDate sd = start.atZone(z).toLocalDate(), ed = end.atZone(z).toLocalDate();
        if (!type.equals("HOTEL")) return List.of(sd);
        List<LocalDate> d = new ArrayList<>();
        for (LocalDate x = sd; x.isBefore(ed); x = x.plusDays(1)) d.add(x);
        return d.isEmpty() ? List.of(sd) : d;
    }

    static int unitsPerPax(String type, Instant start, Instant end, String tz) { return type.equals("HOTEL") ? datesOf(type, start, end, tz).size() : 1; }

    Rules rules() {
        Map<String, Double> m = new HashMap<>();
        for (RuleParam p : RuleParamRepository.findByActiveTrue()) m.put(p.getParamName(), p.getValue());
        return new Rules(m);
    }

    PriceCalculator.Result price(List<TourSegment> segs, int pax) {
        return PriceCalculator.calc(segs.stream().map(s -> new PriceCalculator.Line(s.getUnitPrice(), s.getSeasonK(), s.getUnitsPerPax())).toList(), pax, margin);
    }

    TourSummary summary(Tour t, TourVersion v) {
        BigDecimal per = v.getPax() > 0 ? v.getTotalPrice().divide(BigDecimal.valueOf(v.getPax()), 2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO;
        return new TourSummary(t.getId(), t.getCode(), t.getTitle(), t.getDestinationName(), t.getStatus(), v.getId(), v.getVersionNo(), v.getStartDate(), v.getEndDate(),
                v.getPax(), v.getBudget(), v.getProfile(), v.getTotalPrice(), per, t.getManagerEmail(), t.getDestinationTz(), t.getDestinationRef());
    }

    // ---------- FR-09: СЃРѕР·РґР°РЅРёРµ Рё РїР°СЂР°РјРµС‚СЂС‹ ----------

    @Transactional
    public TourSummary create(TourForm f) {
        CurrentUser me = CurrentUser.get();
        return persistNew(f, me.id(), me.email(), null);
    }

    /** Демо-туры при первом запуске: без сессии пользователя, со стабильным кодом для ссылок витрины. */
    @Transactional
    public TourSummary createSeed(TourForm f, UUID managerId, String email, String code) {
        return persistNew(f, managerId, email, code);
    }

    private TourSummary persistNew(TourForm f, UUID managerId, String email, String code) {
        if (!f.endDate().isAfter(f.startDate())) throw ApiException.invalid("Дата окончания должна быть позже даты начала");
        if (f.startDate().isBefore(LocalDate.now())) throw ApiException.invalid("Дата начала в прошлом");
        if (f.startDate().plusDays(60).isBefore(f.endDate())) throw ApiException.invalid("Длительность тура не более 60 дней");
        DestinationDto d = catalog.destination(f.destinationId());
        Tour t = new Tour();
        t.setCode(code != null ? code : "TF-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        t.setTitle(f.title()); t.setDestinationRef(d.id()); t.setDestinationName(d.name()); t.setDestinationTz(d.tz());
        t.setManagerRef(managerId); t.setManagerEmail(email);
        t = tours.save(t);
        TourVersion v = new TourVersion();
        v.setTourId(t.getId()); v.setVersionNo(1); v.setStartDate(f.startDate()); v.setEndDate(f.endDate()); v.setPax(f.pax()); v.setBudget(f.budget());
        v.setProfile(f.profile() != null ? f.profile() : identity.defaultProfile(managerId)); v.setCreatedBy(managerId);
        v = versions.save(v);
        t.setCurrentVersionId(v.getId());
        return summary(t, v);
    }

    @Transactional
    public Report update(long id, TourForm f) {
        Tour t = tour(id); ensureEditable(t);
        if (!f.endDate().isAfter(f.startDate())) throw ApiException.invalid("Р”Р°С‚Р° РѕРєРѕРЅС‡Р°РЅРёСЏ РґРѕР»Р¶РЅР° Р±С‹С‚СЊ РїРѕР·Р¶Рµ РґР°С‚С‹ РЅР°С‡Р°Р»Р°");
        TourVersion v = current(t);
        t.setTitle(f.title()); t.setUpdatedAt(Instant.now());
        v.setStartDate(f.startDate()); v.setEndDate(f.endDate()); v.setPax(f.pax()); v.setBudget(f.budget());
        if (f.profile() != null) v.setProfile(f.profile());
        return validateAndStore(t, v, false);
    }

    @Transactional(readOnly = true)
    public List<TourSummary> list(String status) {
        List<String> st = status == null || status.isBlank() ? List.of("DRAFT", "VALIDATED", "PUBLISHED", "ARCHIVED") : List.of(status);
        return tours.findByStatusInOrderByUpdatedAtDesc(st).stream().map(t -> summary(t, current(t))).toList();
    }

    @Transactional(readOnly = true)
    public TourView view(long id) {
        Tour t = tour(id); TourVersion v = current(t);
        return new TourView(summary(t, v), segments.findByVersionIdOrderByStartUtcAscIdAsc(v.getId()), lastReport(t, v));
    }

    @Transactional(readOnly = true)
    public List<TourSummary> versionsOf(long id) {
        Tour t = tour(id);
        return versions.findByTourIdOrderByVersionNoDesc(id).stream().map(v -> summary(t, v)).toList();
    }

    // ---------- FR-10: СЃР±РѕСЂРєР° РјР°СЂС€СЂСѓС‚Р° ----------

    @Transactional
    public SegmentResult addSegment(long tourId, SegmentForm f) {
        Tour t = tour(tourId); ensureEditable(t); TourVersion v = current(t);
        CheckResult first = catalog.check(new CheckItem(f.offerId(), List.of(), v.getPax()));
        if (first.offer() == null) throw ApiException.notFound("РћРїС†РёСЏ РєР°С‚Р°Р»РѕРіР°");
        OfferSnapshot o = first.offer();
        if (o.destinationId() != t.getDestinationRef()) throw ApiException.invalid("РћРїС†РёСЏ РѕС‚РЅРѕСЃРёС‚СЃСЏ Рє РґСЂСѓРіРѕРјСѓ РЅР°РїСЂР°РІР»РµРЅРёСЋ");
        Instant start = o.startUtc() != null ? o.startUtc() : f.start();
        Instant end = o.endUtc() != null ? o.endUtc() : f.end();
        if (start == null || end == null) throw ApiException.invalid("РЈРєР°Р¶РёС‚Рµ РІСЂРµРјСЏ РЅР°С‡Р°Р»Р° Рё РѕРєРѕРЅС‡Р°РЅРёСЏ СЃРµРіРјРµРЅС‚Р°");
        if (!end.isAfter(start)) throw ApiException.invalid("РћРєРѕРЅС‡Р°РЅРёРµ РґРѕР»Р¶РЅРѕ Р±С‹С‚СЊ РїРѕР·Р¶Рµ РЅР°С‡Р°Р»Р°");
        CheckResult res = o.startUtc() != null ? first : catalog.check(new CheckItem(o.offerId(), datesOf(o.type(), start, end, o.tz()), v.getPax()));
        TourSegment s = fromSnapshot(res.offer(), start, end, v.getId(), res.minFree());
        s.setEarlyCheckIn(f.earlyCheckIn());
        s.setPosition(segments.maxPosition(v.getId()) + 1);
        s = segments.save(s);
        markDraft(t, v);
        return new SegmentResult(s, validateAndStore(t, v, false));
    }

    TourSegment fromSnapshot(OfferSnapshot o, Instant start, Instant end, Long versionId, int free) {
        TourSegment s = new TourSegment();
        s.setVersionId(versionId);
        applySnapshot(s, o, start, end, free);
        return s;
    }

    void applySnapshot(TourSegment s, OfferSnapshot o, Instant start, Instant end, Integer free) {
        s.setOfferRef(o.offerId()); s.setType(o.type()); s.setTitle(o.title()); s.setStartUtc(start); s.setEndUtc(end); s.setTz(o.tz());
        s.setPlaceFrom(o.placeFrom()); s.setPlaceTo(o.placeTo()); s.setFromLat(o.fromLat()); s.setFromLon(o.fromLon()); s.setToLat(o.toLat()); s.setToLon(o.toLon());
        s.setInternational(o.international()); s.setCheckInFrom(o.checkInFrom()); s.setCheckOutUntil(o.checkOutUntil());
        s.setOpenFrom(o.openFrom()); s.setOpenTo(o.openTo());
        s.setOpenDays(o.openDays() == null ? null : o.openDays().stream().map(String::valueOf).collect(Collectors.joining(",")));
        s.setClassLevel(o.classLevel()); s.setRating(BigDecimal.valueOf(o.rating())); s.setUnitPrice(o.basePrice()); s.setSeasonK(o.seasonK());
        s.setUnitsPerPax(unitsPerPax(o.type(), start, end, o.tz())); s.setCapacity(o.capacity()); s.setFreeSnapshot(free); s.setStale(false);
    }

    @Transactional
    public SegmentResult move(long tourId, long segId, MoveForm f) {
        Tour t = tour(tourId); ensureEditable(t); TourVersion v = current(t);
        TourSegment s = segment(segId, v);
        if (List.of("FLIGHT", "TRAIN", "BUS").contains(s.getType())) throw ApiException.conflict("Р’СЂРµРјСЏ СЂРµР№СЃР° Р·Р°РґР°РЅРѕ РїРѕСЃС‚Р°РІС‰РёРєРѕРј: Р·Р°РјРµРЅРёС‚Рµ РѕРїС†РёСЋ С‡РµСЂРµР· РїРѕРґР±РѕСЂ Р°Р»СЊС‚РµСЂРЅР°С‚РёРІ");
        if (!f.end().isAfter(f.start())) throw ApiException.invalid("РћРєРѕРЅС‡Р°РЅРёРµ РґРѕР»Р¶РЅРѕ Р±С‹С‚СЊ РїРѕР·Р¶Рµ РЅР°С‡Р°Р»Р°");
        s.setStartUtc(f.start()); s.setEndUtc(f.end());
        if (f.earlyCheckIn() != null) s.setEarlyCheckIn(f.earlyCheckIn());
        s.setUnitsPerPax(unitsPerPax(s.getType(), f.start(), f.end(), s.getTz()));
        try {
            CheckResult r = catalog.check(new CheckItem(s.getOfferRef(), datesOf(s), v.getPax()));
            if (r.offer() != null) { s.setFreeSnapshot(r.minFree()); s.setSeasonK(r.offer().seasonK()); s.setStale(false); }
        } catch (ApiException e) { s.setFreeSnapshot(null); }
        markDraft(t, v);
        return new SegmentResult(s, validateAndStore(t, v, false));
    }

    @Transactional
    public Report remove(long tourId, long segId) {
        Tour t = tour(tourId); ensureEditable(t); TourVersion v = current(t);
        segments.delete(segment(segId, v));
        segments.flush();
        markDraft(t, v);
        return validateAndStore(t, v, false);
    }

    TourSegment segment(long segId, TourVersion v) {
        TourSegment s = segments.findById(segId).orElseThrow(() -> ApiException.notFound("РЎРµРіРјРµРЅС‚"));
        if (!s.getVersionId().equals(v.getId())) throw ApiException.notFound("РЎРµРіРјРµРЅС‚");
        return s;
    }

    void markDraft(Tour t, TourVersion v) { t.setStatus("DRAFT"); v.setStatus("DRAFT"); t.setUpdatedAt(Instant.now()); }

    // ---------- FR-11, FR-12, FR-15: РІР°Р»РёРґР°С†РёСЏ РіСЂР°С„РёРєР°, Р°РєС‚СѓР°Р»РёР·Р°С†РёСЏ, С†РµРЅР° ----------

    @Transactional
    public Report validate(long tourId, boolean refresh) {
        Tour t = tour(tourId); TourVersion v = current(t);
        return validateAndStore(t, v, refresh && !t.getStatus().equals("ARCHIVED"));
    }

    /** РђР»РіРѕСЂРёС‚Рј 1: РїРµСЂРµСЃС‡С‘С‚ РіСЂР°С„Р°, Р·Р°РїРёСЃСЊ Р·Р°РїСѓСЃРєР°, РєРѕРЅС„Р»РёРєС‚РѕРІ Рё СЂС‘Р±РµСЂ; РѕР±РЅРѕРІР»РµРЅРёРµ СЃС‚Р°С‚СѓСЃР° DRAFT/VALIDATED. */
    Report validateAndStore(Tour t, TourVersion v, boolean refresh) {
        List<TourSegment> segs = segments.findByVersionIdOrderByStartUtcAscIdAsc(v.getId());
        boolean catalogOk = true;
        if (refresh && !segs.isEmpty()) {
            try {
                List<CheckItem> items = segs.stream().map(s -> new CheckItem(s.getOfferRef(), datesOf(s), v.getPax())).toList();
                List<CheckResult> res = catalog.check(items);
                for (int i = 0; i < segs.size(); i++) {
                    TourSegment s = segs.get(i); CheckResult r = res.get(i);
                    if (r.offer() == null) { s.setFreeSnapshot(0); continue; }
                    s.setFreeSnapshot(r.minFree()); s.setCapacity(r.offer().capacity()); s.setSeasonK(r.offer().seasonK());
                    s.setUnitPrice(r.offer().basePrice()); s.setStale(false);
                }
            } catch (ApiException e) {
                catalogOk = false;
                segs.forEach(s -> s.setFreeSnapshot(null));
            }
        }
        PriceCalculator.Result price = price(segs, v.getPax());
        v.setTotalPrice(price.total());
        ScheduleValidator.TourParams params = new ScheduleValidator.TourParams(v.getStartDate(), v.getEndDate(), t.getDestinationTz(), v.getPax(), v.getBudget(), price.total());
        ScheduleValidator.Result r = ScheduleValidator.validate(params, segs.stream().map(TourService::toSeg).toList(), rules());

        ValidationRun run = new ValidationRun();
        run.setVersionId(v.getId()); run.setErrorsCount((int) r.errors()); run.setWarningsCount((int) r.warnings()); run.setRefreshed(refresh); run.setCatalogAvailable(catalogOk);
        run = runs.save(run);
        for (Conflict c : r.conflicts()) {
            ValidationConflict e = new ValidationConflict();
            e.setRunId(run.getId()); e.setRuleCode(c.rule()); e.setSeverity(c.severity().name()); e.setSegmentAId(c.segA()); e.setSegmentBId(c.segB());
            e.setViolationMin(c.violationMin()); e.setHint(c.hint());
            conflicts.save(e);
        }
        jdbc.update("delete from segment_links where version_id = ?", v.getId());
        for (ScheduleValidator.Link l : r.links())
            jdbc.update("insert into segment_links(version_id, from_segment_id, to_segment_id, required_min, slack_min) values (?,?,?,?,?)",
                    v.getId(), l.fromId(), l.toId(), l.requiredMin(), l.slackMin());

        if (t.getStatus().equals("DRAFT") || t.getStatus().equals("VALIDATED")) {
            String st = r.errors() == 0 && !segs.isEmpty() ? "VALIDATED" : "DRAFT";
            t.setStatus(st); v.setStatus(st);
        }
        return report(t, v, run);
    }

    @Transactional(readOnly = true)
    public Report lastReport(Tour t, TourVersion v) {
        return runs.findFirstByVersionIdOrderByIdDesc(v.getId()).map(run -> report(t, v, run)).orElse(null);
    }

    /** Р—Р°РїСЂРѕСЃ Q1: РєРѕРЅС„Р»РёРєС‚С‹ РІРµСЂСЃРёРё СЃ РЅР°Р·РІР°РЅРёСЏРјРё СЃРµРіРјРµРЅС‚РѕРІ Рё РѕРїРёСЃР°РЅРёРµРј РїСЂР°РІРёР»Р°. */
    Report report(Tour t, TourVersion v, ValidationRun run) {
        List<ConflictView> cv = jdbc.query("""
                select c.id, c.rule_code, c.severity, c.segment_a_id, c.segment_b_id, sa.title as a_title, sb.title as b_title, c.violation_min, c.hint,
                       (select min(r.description) from validation_rules r where r.code = c.rule_code) as rule_desc
                from validation_conflicts c
                join validation_runs vr on vr.id = c.run_id
                left join tour_segments sa on sa.id = c.segment_a_id
                left join tour_segments sb on sb.id = c.segment_b_id
                where vr.id = ?
                order by case c.severity when 'ERROR' then 0 else 1 end, c.rule_code, c.id""",
                (rs, i) -> new ConflictView(rs.getLong("id"), rs.getString("rule_code"), rs.getString("severity"), (Long) rs.getObject("segment_a_id"),
                        (Long) rs.getObject("segment_b_id"), rs.getString("a_title"), rs.getString("b_title"), rs.getLong("violation_min"),
                        rs.getString("hint"), rs.getString("rule_desc")), run.getId());
        List<LinkView> links = jdbc.query("select from_segment_id, to_segment_id, required_min, slack_min from segment_links where version_id = ? order by id",
                (rs, i) -> new LinkView(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4), rs.getLong(4) == 0), v.getId());
        PriceCalculator.Result p = price(segments.findByVersionIdOrderByStartUtcAscIdAsc(v.getId()), v.getPax());
        return new Report(run.getId(), run.getErrorsCount(), run.getWarningsCount(), run.isCatalogAvailable(), run.isRefreshed(), cv, links,
                new PriceView(p.subtotal(), p.discountRate(), margin, p.total(), p.perPerson(), v.getPax()), t.getStatus());
    }

    @Transactional(readOnly = true)
    public PriceView priceFor(long tourId, Integer pax) {
        Tour t = tour(tourId); TourVersion v = current(t);
        int n = pax == null ? v.getPax() : pax;
        if (n < 1 || n > 200) throw ApiException.invalid("Р§РёСЃР»Рѕ СѓС‡Р°СЃС‚РЅРёРєРѕРІ РѕС‚ 1 РґРѕ 200");
        PriceCalculator.Result p = price(segments.findByVersionIdOrderByStartUtcAscIdAsc(v.getId()), n);
        return new PriceView(p.subtotal(), p.discountRate(), margin, p.total(), p.perPerson(), n);
    }

    // ---------- FR-17: РїСѓР±Р»РёРєР°С†РёСЏ, FR-16: РІРµСЂСЃРёРё ----------

    /** noRollbackFor: СЂРµР·СѓР»СЊС‚Р°С‚С‹ РїСЂРѕРІРµСЂРєРё Рё СЃРѕР±С‹С‚РёРµ tour.validation.failed СЃРѕС…СЂР°РЅСЏСЋС‚СЃСЏ РґР°Р¶Рµ РїСЂРё РѕС‚РєР°Р·Рµ РїСѓР±Р»РёРєР°С†РёРё. */
    @Transactional(noRollbackFor = ApiException.class)
    public TourSummary publish(long tourId) {
        Tour t = tour(tourId);
        if (t.getStatus().equals("PUBLISHED")) throw ApiException.conflict("Тур уже опубликован");
        if (t.getStatus().equals("ARCHIVED")) throw ApiException.conflict("Тур в архиве");
        TourVersion v = current(t);
        Report rep = validateAndStore(t, v, true);
        if (!rep.catalogAvailable()) throw ApiException.unavailable("Каталог недоступен: публикация невозможна без проверки доступности");
        if (rep.errors() > 0) {
            outbox.publish("tour.validation.failed", String.valueOf(t.getId()), Map.of("tourId", t.getId(), "title", t.getTitle(),
                    "managerEmail", String.valueOf(t.getManagerEmail()), "errors", rep.errors()));
            throw new ApiException(org.springframework.http.HttpStatus.CONFLICT, "VALIDATION_FAILED", "Публикация невозможна: найдено ошибок — " + rep.errors());
        }
        t.setStatus("PUBLISHED"); v.setStatus("PUBLISHED"); t.setUpdatedAt(Instant.now());
        outbox.publish("tour.published", String.valueOf(t.getId()), Map.of("tourId", t.getId(), "title", t.getTitle(), "managerEmail", String.valueOf(t.getManagerEmail()),
                "versionNo", v.getVersionNo(), "totalPrice", v.getTotalPrice()));
        return summary(t, v);
    }

    @Transactional
    public TourSummary newVersion(long tourId) {
        Tour t = tour(tourId); TourVersion old = current(t);
        if (t.getStatus().equals("ARCHIVED")) throw ApiException.conflict("Тур в архиве");
        TourVersion nv = new TourVersion();
        nv.setTourId(t.getId()); nv.setVersionNo(versions.findByTourIdOrderByVersionNoDesc(t.getId()).get(0).getVersionNo() + 1);
        nv.setStartDate(old.getStartDate()); nv.setEndDate(old.getEndDate()); nv.setPax(old.getPax()); nv.setBudget(old.getBudget());
        nv.setProfile(old.getProfile()); nv.setCreatedBy(CurrentUser.get().id());
        nv = versions.save(nv);
        for (TourSegment s : segments.findByVersionIdOrderByStartUtcAscIdAsc(old.getId())) {
            TourSegment c = new TourSegment();
            org.springframework.beans.BeanUtils.copyProperties(s, c, "id", "versionId");
            c.setVersionId(nv.getId());
            segments.save(c);
        }
        old.setStatus("ARCHIVED");
        t.setCurrentVersionId(nv.getId()); t.setStatus("DRAFT"); t.setUpdatedAt(Instant.now());
        validateAndStore(t, nv, false);
        return summary(t, nv);
    }
}

