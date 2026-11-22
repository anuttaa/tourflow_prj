package com.tourflow.catalog;

import com.tourflow.catalog.CatalogService.PeriodForm;
import com.tourflow.catalog.CatalogService.ScheduleForm;
import com.tourflow.catalog.model.*;
import com.tourflow.catalog.repository.*;
import com.tourflow.common.web.ApiException;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** FR-05, FR-06, FR-07, FR-08: РєР°С‚Р°Р»РѕРі РІРµРґС‘С‚ РјРµРЅРµРґР¶РµСЂ (Рё Р°РґРјРёРЅРёСЃС‚СЂР°С‚РѕСЂ). */
@RestController
@RequestMapping("/api/v1/catalog")
@PreAuthorize("hasAnyRole('TOUR_MANAGER','ADMIN')")
public class CatalogController {
    private final DestinationRepository destinations; private final SupplierRepository suppliers; private final OfferRepository offers; private final CatalogService service;

    public CatalogController(DestinationRepository destinations, SupplierRepository suppliers, OfferRepository offers, CatalogService service) {
        this.destinations = destinations; this.suppliers = suppliers; this.offers = offers; this.service = service;
    }

    public record DestinationForm(@NotBlank @Size(max = 120) String name, @NotBlank @Size(max = 80) String country, @NotBlank String tz, Double lat, Double lon) {}
    public record SupplierForm(@NotBlank @Size(max = 150) String name, @Size(max = 200) String contact, boolean active) {}
    public record AvailabilityForm(@NotNull LocalDate from, @NotNull LocalDate to, @Min(0) @Max(100000) int quota) {}
    public record OfferForm(@NotNull Long supplierId, @NotNull Long destinationId,
                            @NotBlank @Pattern(regexp = "FLIGHT|TRAIN|BUS|HOTEL|TRANSFER|EXCURSION|INSURANCE|MEAL") String type,
                            @NotBlank @Size(max = 200) String title, @Size(max = 2000) String description,
                            @Min(1) @Max(5) int classLevel, @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal basePrice,
                            @Min(1) int capacity, Instant startUtc, Instant endUtc, @NotBlank String tz,
                            String placeFrom, String placeTo, Double fromLat, Double fromLon, Double toLat, Double toLon,
                            boolean international, Integer checkInFrom, Integer checkOutUntil, String attrs,
                            List<ScheduleForm> schedules, List<PeriodForm> pricePeriods) {}

    private static void tzCheck(String tz) { try { ZoneId.of(tz); } catch (Exception e) { throw ApiException.invalid("РќРµРёР·РІРµСЃС‚РЅС‹Р№ С‡Р°СЃРѕРІРѕР№ РїРѕСЏСЃ: " + tz); } }

    // --- РЅР°РїСЂР°РІР»РµРЅРёСЏ Рё РїРѕСЃС‚Р°РІС‰РёРєРё ---
    @GetMapping("/destinations") public List<Destination> destinations() { return destinations.findAll(Sort.by("name")); }

    @PostMapping("/destinations") @ResponseStatus(HttpStatus.CREATED)
    public Destination createDestination(@Valid @RequestBody DestinationForm f) { return saveDest(new Destination(), f); }

    @PutMapping("/destinations/{id}")
    public Destination updateDestination(@PathVariable long id, @Valid @RequestBody DestinationForm f) {
        return saveDest(destinations.findById(id).orElseThrow(() -> ApiException.notFound("РќР°РїСЂР°РІР»РµРЅРёРµ")), f);
    }

    private Destination saveDest(Destination d, DestinationForm f) {
        tzCheck(f.tz());
        d.setName(f.name()); d.setCountry(f.country()); d.setTz(f.tz()); d.setLat(f.lat()); d.setLon(f.lon());
        return destinations.save(d);
    }

    @DeleteMapping("/destinations/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDestination(@PathVariable long id) { destinations.deleteById(id); }

    @GetMapping("/suppliers") public List<Supplier> suppliers() { return suppliers.findAll(Sort.by("name")); }

    @PostMapping("/suppliers") @ResponseStatus(HttpStatus.CREATED)
    public Supplier createSupplier(@Valid @RequestBody SupplierForm f) { return saveSup(new Supplier(), f); }

    @PutMapping("/suppliers/{id}")
    public Supplier updateSupplier(@PathVariable long id, @Valid @RequestBody SupplierForm f) {
        return saveSup(suppliers.findById(id).orElseThrow(() -> ApiException.notFound("РџРѕСЃС‚Р°РІС‰РёРє")), f);
    }

    private Supplier saveSup(Supplier s, SupplierForm f) { s.setName(f.name()); s.setContact(f.contact()); s.setActive(f.active()); return suppliers.save(s); }

    @DeleteMapping("/suppliers/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSupplier(@PathVariable long id) { suppliers.deleteById(id); }

    // --- РѕРїС†РёРё: FR-08 РїРѕРёСЃРє, С„РёР»СЊС‚СЂР°С†РёСЏ, СЃРѕСЂС‚РёСЂРѕРІРєР°, СЃС‚СЂР°РЅРёС†С‹ ---
    @GetMapping("/offers")
    public Page<Offer> search(@RequestParam(required = false) String q, @RequestParam(required = false) Long destinationId,
                              @RequestParam(required = false) String type, @RequestParam(required = false) Long supplierId,
                              @RequestParam(required = false) BigDecimal minPrice, @RequestParam(required = false) BigDecimal maxPrice,
                              @RequestParam(required = false) BigDecimal minRating,
                              @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
                              @RequestParam(defaultValue = "title,asc") String sort,
                              @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        Specification<Offer> spec = (r, cq, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.isTrue(r.get("active")));
            if (q != null && !q.isBlank()) p.add(cb.like(cb.lower(r.get("title")), "%" + q.toLowerCase() + "%"));
            if (destinationId != null) p.add(cb.equal(r.get("destinationId"), destinationId));
            if (type != null && !type.isBlank()) p.add(cb.equal(r.get("type"), type));
            if (supplierId != null) p.add(cb.equal(r.get("supplierId"), supplierId));
            if (minPrice != null) p.add(cb.ge(r.get("basePrice"), minPrice));
            if (maxPrice != null) p.add(cb.le(r.get("basePrice"), maxPrice));
            if (minRating != null) p.add(cb.ge(r.get("ratingAvg"), minRating));
            if (from != null) p.add(cb.greaterThanOrEqualTo(r.get("startUtc"), from));
            if (to != null) p.add(cb.lessThanOrEqualTo(r.get("startUtc"), to));
            return cb.and(p.toArray(new Predicate[0]));
        };
        String[] s = sort.split(",");
        String field = List.of("title", "basePrice", "ratingAvg", "startUtc", "classLevel", "type").contains(s[0]) ? s[0] : "title";
        Sort.Direction dir = s.length > 1 && s[1].equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        return offers.findAll(spec, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(dir, field)));
    }

    @GetMapping("/offers/{id}") public Map<String, Object> offer(@PathVariable long id) { return service.details(id); }

    @PostMapping("/offers") @ResponseStatus(HttpStatus.CREATED)
    public Offer create(@Valid @RequestBody OfferForm f) { return persist(new Offer(), f); }

    @PutMapping("/offers/{id}")
    public Offer update(@PathVariable long id, @Valid @RequestBody OfferForm f) {
        return persist(offers.findById(id).orElseThrow(() -> ApiException.notFound("РћРїС†РёСЏ")), f);
    }

    @DeleteMapping("/offers/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) { service.deactivate(id); }

    private Offer persist(Offer o, OfferForm f) {
        tzCheck(f.tz());
        if (!suppliers.existsById(f.supplierId())) throw ApiException.invalid("РџРѕСЃС‚Р°РІС‰РёРє РЅРµ РЅР°Р№РґРµРЅ");
        if (!destinations.existsById(f.destinationId())) throw ApiException.invalid("РќР°РїСЂР°РІР»РµРЅРёРµ РЅРµ РЅР°Р№РґРµРЅРѕ");
        boolean timed = List.of("FLIGHT", "TRAIN", "BUS").contains(f.type());
        if (timed && (f.startUtc() == null || f.endUtc() == null)) throw ApiException.invalid("Р”Р»СЏ РїРµСЂРµРІРѕР·РєРё СѓРєР°Р¶РёС‚Рµ РІСЂРµРјСЏ РѕС‚РїСЂР°РІР»РµРЅРёСЏ Рё РїСЂРёР±С‹С‚РёСЏ");
        if (f.startUtc() != null && f.endUtc() != null && !f.endUtc().isAfter(f.startUtc())) throw ApiException.invalid("РџСЂРёР±С‹С‚РёРµ РґРѕР»Р¶РЅРѕ Р±С‹С‚СЊ РїРѕР·Р¶Рµ РѕС‚РїСЂР°РІР»РµРЅРёСЏ");
        if (f.schedules() != null) for (ScheduleForm s : f.schedules())
            if (s.dayOfWeek() < 1 || s.dayOfWeek() > 7 || s.openFrom() < 0 || s.openTo() > 1440 || s.openTo() <= s.openFrom())
                throw ApiException.invalid("РќРµРєРѕСЂСЂРµРєС‚РЅРѕРµ СЂР°СЃРїРёСЃР°РЅРёРµ СЂР°Р±РѕС‚С‹");
        if (f.pricePeriods() != null) for (PeriodForm p : f.pricePeriods())
            if (p.dateFrom() == null || p.dateTo() == null || p.dateTo().isBefore(p.dateFrom()) || p.coefficient() == null || p.coefficient().signum() <= 0)
                throw ApiException.invalid("РќРµРєРѕСЂСЂРµРєС‚РЅС‹Р№ СЃРµР·РѕРЅРЅС‹Р№ РїРµСЂРёРѕРґ С†РµРЅС‹");
        o.setSupplierId(f.supplierId()); o.setDestinationId(f.destinationId()); o.setType(f.type()); o.setTitle(f.title());
        o.setDescription(f.description()); o.setClassLevel(f.classLevel()); o.setBasePrice(f.basePrice()); o.setCapacity(f.capacity());
        o.setStartUtc(f.startUtc()); o.setEndUtc(f.endUtc()); o.setTz(f.tz()); o.setPlaceFrom(f.placeFrom()); o.setPlaceTo(f.placeTo());
        o.setFromLat(f.fromLat()); o.setFromLon(f.fromLon()); o.setToLat(f.toLat()); o.setToLon(f.toLon());
        o.setInternational(f.international()); o.setCheckInFrom(f.checkInFrom()); o.setCheckOutUntil(f.checkOutUntil());
        o.setAttrs(f.attrs() == null || f.attrs().isBlank() ? "{}" : f.attrs());
        return service.save(o, f.schedules(), f.pricePeriods());
    }

    // --- FR-07: РєР°Р»РµРЅРґР°СЂСЊ РґРѕСЃС‚СѓРїРЅРѕСЃС‚Рё ---
    @PutMapping("/offers/{id}/availability")
    public Map<String, Object> setAvailability(@PathVariable long id, @Valid @RequestBody AvailabilityForm f) {
        return Map.of("updatedDays", service.setAvailability(id, f.from(), f.to(), f.quota()));
    }

    @GetMapping("/offers/{id}/availability")
    public List<Availability> availability(@PathVariable long id, @RequestParam LocalDate from, @RequestParam LocalDate to) {
        if (to.isBefore(from) || from.plusDays(400).isBefore(to)) throw ApiException.invalid("РќРµРєРѕСЂСЂРµРєС‚РЅС‹Р№ РїРµСЂРёРѕРґ");
        return service.availability(id, from, to);
    }
}

