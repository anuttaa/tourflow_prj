package com.tourflow.planning;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** FR-13, FR-14: Р В°Р Р†РЎвЂљР С•Р С—Р С•Р Т‘Р В±Р С•РЎР‚ Р В°Р В»РЎРЉРЎвЂљР ВµРЎР‚Р Р…Р В°РЎвЂљР С‘Р Р† (Р В°Р В»Р С–Р С•РЎР‚Р С‘РЎвЂљР С 2) Р С‘ Р С‘РЎвЂ¦ Р С—РЎР‚Р С‘Р СР ВµР Р…Р ВµР Р…Р С‘Р Вµ РЎРѓ Р С”Р В°РЎРѓР С”Р В°Р Т‘Р Р…Р С•Р в„– Р С—Р ВµРЎР‚Р ВµР Р†Р В°Р В»Р С‘Р Т‘Р В°РЎвЂ Р С‘Р ВµР в„–. */
@Service
public class AlternativeService {
    private final TourService tours; private final CatalogClient catalog; private final AlternativeRequestRepository requests; private final AlternativeCandidateRepository candidates;
    private final TourSegmentRepository segments; private final OutboxService outbox; private final ObjectMapper mapper; private final TransactionTemplate requiresNew;

    public AlternativeService(TourService tours, CatalogClient catalog, AlternativeRequestRepository requests, AlternativeCandidateRepository candidates, TourSegmentRepository segments,
                              OutboxService outbox, ObjectMapper mapper, PlatformTransactionManager tm) {
        this.tours = tours; this.catalog = catalog; this.requests = requests; this.candidates = candidates; this.segments = segments;
        this.outbox = outbox; this.mapper = mapper;
        this.requiresNew = new TransactionTemplate(tm);
        this.requiresNew.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    private static List<String> compatibleTypes(String type) {
        return type.equals("BUS") || type.equals("TRAIN") ? List.of("BUS", "TRAIN") : List.of(type);
    }

    private static String key(Conflict c) { return c.rule() + "|" + c.segA() + "|" + c.segB(); }

    /** Р вЂ“РЎвЂРЎРѓРЎвЂљР С”Р С‘Р Вµ Р С•Р С–РЎР‚Р В°Р Р…Р С‘РЎвЂЎР ВµР Р…Р С‘РЎРЏ: Р С—Р С•Р Т‘РЎРѓРЎвЂљР В°Р Р…Р С•Р Р†Р С”Р В° Р Р…Р Вµ Р Т‘Р С•Р В»Р В¶Р Р…Р В° Р Т‘Р В°Р Р†Р В°РЎвЂљРЎРЉ Р Р…Р С•Р Р†РЎвЂ№РЎвЂ¦ Error (Р Р† РЎвЂљ.РЎвЂЎ. Р С—Р С• Р В±РЎР‹Р Т‘Р В¶Р ВµРЎвЂљРЎС“) Р С‘ Error Р С—Р С• РЎРѓР В°Р СР С•Р СРЎС“ Р С”Р В°Р Р…Р Т‘Р С‘Р Т‘Р В°РЎвЂљРЎС“. */
    private boolean feasible(Tour t, TourVersion v, List<TourSegment> all, TourSegment orig, TourSegment cand, Rules rules, long baseErrorsWithoutOrig) {
        List<TourSegment> copy = new ArrayList<>();
        for (TourSegment s : all) copy.add(s.getId().equals(orig.getId()) ? cand : s);
        PriceCalculator.Result p = tours.price(copy, v.getPax());
        ScheduleValidator.Result r = ScheduleValidator.validate(new ScheduleValidator.TourParams(v.getStartDate(), v.getEndDate(), t.getDestinationTz(), v.getPax(), v.getBudget(), p.total()),
                copy.stream().map(TourService::toSeg).toList(), rules);
        long id = orig.getId();
        boolean involvesCand = r.conflicts().stream().anyMatch(c -> c.severity() == Severity.ERROR && c.involves(id));
        long others = r.conflicts().stream().filter(c -> c.severity() == Severity.ERROR && !c.involves(id)).count();
        return !involvesCand && others <= baseErrorsWithoutOrig;
    }

    @Transactional
    public AlternativesView suggest(long tourId, AltForm f) {
        Tour t = tours.tour(tourId); tours.ensureEditable(t);
        TourVersion v = tours.current(t);
        List<TourSegment> all = segments.findByVersionIdOrderByStartUtcAscIdAsc(v.getId());
        TourSegment orig = tours.segment(f.segmentId(), v);
        String profile = f.profile() != null ? f.profile() : v.getProfile();
        int delta = f.deltaMinutes() != null ? f.deltaMinutes() : 180;
        int topN = f.topN() != null ? f.topN() : 3;
        Rules rules = tours.rules();

        CandidateRequest req = new CandidateRequest(compatibleTypes(orig.getType()), t.getDestinationRef(), orig.getStartUtc().minus(Duration.ofMinutes(delta)),
                orig.getEndUtc().plus(Duration.ofMinutes(delta)), v.getPax(), TourService.datesOf(orig), orig.getOfferRef(), 40);
        List<CheckResult> found = catalog.candidates(req);

        Seg origSeg = TourService.toSeg(orig);
        long baseOthers = baseErrorsWithoutOrig(t, v, all, orig, rules);
        List<Seg> fg = all.stream().filter(s -> !s.getId().equals(orig.getId())).map(TourService::toSeg).filter(s -> !s.type().background())
                .sorted(Comparator.comparing(Seg::start)).toList();
        Seg prev = fg.stream().filter(s -> !s.end().isAfter(orig.getStartUtc())).reduce((a, b) -> b).orElse(null);
        Seg next = fg.stream().filter(s -> !s.start().isBefore(orig.getEndUtc())).findFirst().orElse(null);

        Map<Long, TourSegment> byOffer = new HashMap<>();
        List<AlternativeEngine.Cand> cands = new ArrayList<>();
        for (CheckResult r : found) {
            OfferSnapshot o = r.offer();
            Instant start = o.startUtc() != null ? o.startUtc() : orig.getStartUtc();
            Instant end = o.endUtc() != null ? o.endUtc() : orig.getEndUtc();
            TourSegment cs = tours.fromSnapshot(o, start, end, v.getId(), r.minFree());
            cs.setId(orig.getId());
            cs.setEarlyCheckIn(orig.isEarlyCheckIn());
            byOffer.put(o.offerId(), cs);
            BigDecimal per = o.basePrice().multiply(o.seasonK()).multiply(BigDecimal.valueOf(cs.getUnitsPerPax()));
            cands.add(new AlternativeEngine.Cand(o.offerId(), TourService.toSeg(cs), per, o.rating(), o.classLevel()));
        }
        BigDecimal origPrice = orig.getUnitPrice().multiply(orig.getSeasonK()).multiply(BigDecimal.valueOf(orig.getUnitsPerPax()));
        List<AlternativeEngine.Scored> ranked = AlternativeEngine.rank(cands, origSeg, orig.getClassLevel(), origPrice, prev, next, delta,
                AlternativeEngine.weights(profile), topN, c -> feasible(t, v, all, orig, byOffer.get(c.offerRef()), rules, baseOthers));

        AlternativeRequest ar = new AlternativeRequest();
        ar.setVersionId(v.getId()); ar.setSegmentId(orig.getId()); ar.setOriginalOfferRef(orig.getOfferRef()); ar.setSegmentType(orig.getType());
        ar.setProfile(profile); ar.setWindowDeltaMin(delta); ar.setRequestedBy(CurrentUser.get().id());
        ar = requests.save(ar);

        List<CandidateView> views = new ArrayList<>();
        for (AlternativeEngine.Scored sc : ranked) {
            TourSegment cs = byOffer.get(sc.cand().offerRef());
            AlternativeCandidate ac = new AlternativeCandidate();
            ac.setRequestId(ar.getId()); ac.setOfferRef(sc.cand().offerRef()); ac.setScore(BigDecimal.valueOf(sc.score())); ac.setRank(sc.rank());
            Map<String, Object> expl = new LinkedHashMap<>();
            expl.put("contributions", sc.contributions()); expl.put("deltaStartMin", sc.deltaStartMin()); expl.put("deltaPrice", sc.deltaPrice());
            expl.put("text", sc.explanation()); expl.put("segment", specOf(cs));
            try { ac.setExplanation(mapper.writeValueAsString(expl)); } catch (JsonProcessingException e) { throw new IllegalStateException(e); }
            ac = candidates.save(ac);
            views.add(new CandidateView(ac.getId(), sc.rank(), sc.score(), sc.cand().offerRef(), cs.getType(), cs.getTitle(), cs.getStartUtc(), cs.getEndUtc(), cs.getTz(),
                    sc.cand().pricePerPax(), sc.cand().rating(), sc.cand().classLevel(), sc.deltaStartMin(), sc.deltaPrice(), sc.explanation(), sc.contributions()));
        }
        List<String> hints = views.isEmpty()
                ? List.of("Расширьте окно подбора по времени (например, до ±6 или ±12 часов)", "Попробуйте другой профиль подбора (эконом, баланс или комфорт)", "Скорректируйте даты поездки или увеличьте бюджет тура")
                : List.of();
        return new AlternativesView(ar.getId(), views.isEmpty(), views, hints);
    }

    private long baseErrorsWithoutOrig(Tour t, TourVersion v, List<TourSegment> all, TourSegment orig, Rules rules) {
        PriceCalculator.Result p = tours.price(all, v.getPax());
        ScheduleValidator.Result r = ScheduleValidator.validate(new ScheduleValidator.TourParams(v.getStartDate(), v.getEndDate(), t.getDestinationTz(), v.getPax(), v.getBudget(), p.total()),
                all.stream().map(TourService::toSeg).toList(), rules);
        return r.conflicts().stream().filter(c -> c.severity() == Severity.ERROR && !c.involves(orig.getId())).count();
    }

    private Map<String, Object> specOf(TourSegment s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("offerRef", s.getOfferRef()); m.put("type", s.getType()); m.put("title", s.getTitle()); m.put("start", s.getStartUtc().toString()); m.put("end", s.getEndUtc().toString());
        m.put("tz", s.getTz()); m.put("placeFrom", s.getPlaceFrom()); m.put("placeTo", s.getPlaceTo());
        m.put("fromLat", s.getFromLat()); m.put("fromLon", s.getFromLon()); m.put("toLat", s.getToLat()); m.put("toLon", s.getToLon());
        m.put("international", s.isInternational()); m.put("checkInFrom", s.getCheckInFrom()); m.put("checkOutUntil", s.getCheckOutUntil());
        m.put("openFrom", s.getOpenFrom()); m.put("openTo", s.getOpenTo()); m.put("openDays", s.getOpenDays());
        m.put("classLevel", s.getClassLevel()); m.put("rating", s.getRating()); m.put("unitPrice", s.getUnitPrice()); m.put("seasonK", s.getSeasonK());
        m.put("unitsPerPax", s.getUnitsPerPax()); m.put("capacity", s.getCapacity()); m.put("free", s.getFreeSnapshot());
        return m;
    }

    private TourSegment fromSpec(JsonNode n, Long versionId, Long id, boolean earlyCheckIn) {
        TourSegment s = new TourSegment();
        s.setId(id); s.setVersionId(versionId);
        s.setOfferRef(n.get("offerRef").asLong()); s.setType(n.get("type").asText()); s.setTitle(n.get("title").asText());
        s.setStartUtc(Instant.parse(n.get("start").asText())); s.setEndUtc(Instant.parse(n.get("end").asText())); s.setTz(n.get("tz").asText());
        s.setPlaceFrom(text(n, "placeFrom")); s.setPlaceTo(text(n, "placeTo"));
        s.setFromLat(dbl(n, "fromLat")); s.setFromLon(dbl(n, "fromLon")); s.setToLat(dbl(n, "toLat")); s.setToLon(dbl(n, "toLon"));
        s.setInternational(n.get("international").asBoolean()); s.setCheckInFrom(intg(n, "checkInFrom")); s.setCheckOutUntil(intg(n, "checkOutUntil"));
        s.setOpenFrom(intg(n, "openFrom")); s.setOpenTo(intg(n, "openTo")); s.setOpenDays(text(n, "openDays"));
        s.setClassLevel(n.get("classLevel").asInt()); s.setRating(new BigDecimal(n.get("rating").asText())); s.setUnitPrice(new BigDecimal(n.get("unitPrice").asText()));
        s.setSeasonK(new BigDecimal(n.get("seasonK").asText())); s.setUnitsPerPax(n.get("unitsPerPax").asInt()); s.setCapacity(intg(n, "capacity")); s.setFreeSnapshot(intg(n, "free"));
        s.setEarlyCheckIn(earlyCheckIn);
        return s;
    }
    private static String text(JsonNode n, String f) { return n.hasNonNull(f) ? n.get(f).asText() : null; }
    private static Double dbl(JsonNode n, String f) { return n.hasNonNull(f) ? n.get(f).asDouble() : null; }
    private static Integer intg(JsonNode n, String f) { return n.hasNonNull(f) ? n.get(f).asInt() : null; }

    /**
     * FR-14: Р С—РЎР‚Р С‘Р СР ВµР Р…Р ВµР Р…Р С‘Р Вµ Р Р† Р С•Р Т‘Р Р…Р С•Р в„– РЎвЂљРЎР‚Р В°Р Р…Р В·Р В°Р С”РЎвЂ Р С‘Р С‘. Р СџР С•Р Т‘РЎРѓРЎвЂљР В°Р Р…Р С•Р Р†Р С”Р В° Р С—РЎР‚Р С•Р Р†Р ВµРЎР‚РЎРЏР ВµРЎвЂљРЎРѓРЎРЏ Р Р…Р В° Р С”Р С•Р С—Р С‘Р С‘; Р С—РЎР‚Р С‘ Р Р…Р С•Р Р†РЎвЂ№РЎвЂ¦ Error РЎР‚Р ВµРЎв‚¬Р ВµР Р…Р С‘Р Вµ РЎвЂћР С‘Р С”РЎРѓР С‘РЎР‚РЎС“Р ВµРЎвЂљРЎРѓРЎРЏ Р С”Р В°Р С” REJECTED
     * Р Р† Р С•РЎвЂљР Т‘Р ВµР В»РЎРЉР Р…Р С•Р в„– РЎвЂљРЎР‚Р В°Р Р…Р В·Р В°Р С”РЎвЂ Р С‘Р С‘, Р С•РЎРѓР Р…Р С•Р Р†Р Р…Р В°РЎРЏ РЎвЂљРЎР‚Р В°Р Р…Р В·Р В°Р С”РЎвЂ Р С‘РЎРЏ Р С•РЎвЂљР С”Р В°РЎвЂљРЎвЂ№Р Р†Р В°Р ВµРЎвЂљРЎРѓРЎРЏ.
     */
    @Transactional
    public SegmentResult apply(long tourId, long requestId, long candidateId) {
        Tour t = tours.tour(tourId); tours.ensureEditable(t);
        TourVersion v = tours.current(t);
        AlternativeRequest ar = requests.findById(requestId).orElseThrow(() -> ApiException.notFound("Р вЂ”Р В°Р С—РЎР‚Р С•РЎРѓ Р С—Р С•Р Т‘Р В±Р С•РЎР‚Р В°"));
        AlternativeCandidate ac = candidates.findById(candidateId).orElseThrow(() -> ApiException.notFound("Р С’Р В»РЎРЉРЎвЂљР ВµРЎР‚Р Р…Р В°РЎвЂљР С‘Р Р†Р В°"));
        if (!ar.getVersionId().equals(v.getId()) || !ac.getRequestId().equals(ar.getId())) throw ApiException.notFound("Р С’Р В»РЎРЉРЎвЂљР ВµРЎР‚Р Р…Р В°РЎвЂљР С‘Р Р†Р В°");
        if (!ac.getDecision().equals("NEW")) throw ApiException.conflict("Р В Р ВµРЎв‚¬Р ВµР Р…Р С‘Р Вµ Р С—Р С• РЎРЊРЎвЂљР С•Р в„– Р В°Р В»РЎРЉРЎвЂљР ВµРЎР‚Р Р…Р В°РЎвЂљР С‘Р Р†Р Вµ РЎС“Р В¶Р Вµ Р С—РЎР‚Р С‘Р Р…РЎРЏРЎвЂљР С•");
        TourSegment seg = tours.segment(ar.getSegmentId(), v);
        JsonNode spec;
        try { spec = mapper.readTree(ac.getExplanation()).get("segment"); } catch (JsonProcessingException e) { throw new IllegalStateException(e); }
        TourSegment cand = fromSpec(spec, v.getId(), seg.getId(), seg.isEarlyCheckIn());

        List<TourSegment> all = segments.findByVersionIdOrderByStartUtcAscIdAsc(v.getId());
        Rules rules = tours.rules();
        if (!feasible(t, v, all, seg, cand, rules, baseErrorsWithoutOrig(t, v, all, seg, rules))) {
            long id = ac.getId();
            requiresNew.executeWithoutResult(st -> candidates.findById(id).ifPresent(x -> x.setDecision("REJECTED")));
            throw ApiException.conflict("Р СџР С•Р Т‘РЎРѓРЎвЂљР В°Р Р…Р С•Р Р†Р С”Р В° РЎРѓР С•Р В·Р Т‘Р В°РЎвЂРЎвЂљ Р Р…Р С•Р Р†РЎвЂ№Р Вµ Р С”Р С•Р Р…РЎвЂћР В»Р С‘Р С”РЎвЂљРЎвЂ№ (Р Т‘Р В°Р Р…Р Р…РЎвЂ№Р Вµ Р С‘Р В·Р СР ВµР Р…Р С‘Р В»Р С‘РЎРѓРЎРЉ), Р Р†РЎвЂ№Р В±Р ВµРЎР‚Р С‘РЎвЂљР Вµ Р Т‘РЎР‚РЎС“Р С–Р С•Р в„– Р Р†Р В°РЎР‚Р С‘Р В°Р Р…РЎвЂљ");
        }
        Long oldOffer = seg.getOfferRef();
        BeanUtilsCopy.copy(cand, seg);
        ac.setDecision("ACCEPTED");
        for (AlternativeCandidate other : candidates.findByRequestIdOrderByRankAsc(ar.getId()))
            if (!other.getId().equals(ac.getId()) && other.getDecision().equals("NEW")) other.setDecision("REJECTED");
        tours.markDraft(t, v);
        Report rep = tours.validateAndStore(t, v, false);
        outbox.publish("alternative.applied", String.valueOf(t.getId()), Map.of("tourId", t.getId(), "segmentId", seg.getId(), "oldOfferRef", oldOffer,
                "newOfferRef", seg.getOfferRef(), "type", seg.getType(), "profile", ar.getProfile()));
        return new SegmentResult(seg, rep);
    }

    /** Р С™Р С•Р С—Р С‘РЎР‚Р С•Р Р†Р В°Р Р…Р С‘Р Вµ Р С—Р С•Р В»Р ВµР в„– Р С—Р С•Р Т‘РЎРѓРЎвЂљР В°Р Р…Р С•Р Р†Р С”Р С‘ Р Р† РЎРѓРЎС“РЎвЂ°Р ВµРЎРѓРЎвЂљР Р†РЎС“РЎР‹РЎвЂ°Р С‘Р в„– РЎРѓР ВµР С–Р СР ВµР Р…РЎвЂљ (РЎРѓРЎвЂљРЎР‚Р С•Р С”Р В° РЎРѓР С•РЎвЂ¦РЎР‚Р В°Р Р…РЎРЏР ВµРЎвЂљРЎРѓРЎРЏ, Р Р†Р Р…Р ВµРЎв‚¬Р Р…Р С‘Р Вµ Р С”Р В»РЎР‹РЎвЂЎР С‘ Р С”Р С•Р Р…РЎвЂћР В»Р С‘Р С”РЎвЂљР С•Р Р† Р С•РЎРѓРЎвЂљР В°РЎР‹РЎвЂљРЎРѓРЎРЏ Р Р†Р В°Р В»Р С‘Р Т‘Р Р…РЎвЂ№Р СР С‘). */
    static final class BeanUtilsCopy {
        static void copy(TourSegment from, TourSegment to) {
            org.springframework.beans.BeanUtils.copyProperties(from, to, "id", "versionId", "position");
        }
    }
}

