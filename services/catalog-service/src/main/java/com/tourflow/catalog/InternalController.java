package com.tourflow.catalog;

import com.tourflow.catalog.model.Destination;
import com.tourflow.catalog.repository.DestinationRepository;
import com.tourflow.common.dto.Contracts.*;
import com.tourflow.common.web.ApiException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Р’РЅСѓС‚СЂРµРЅРЅРёР№ REST-РєРѕРЅС‚СЂР°РєС‚ catalog (РўР— 10.2); РЅР°СЂСѓР¶Сѓ С‡РµСЂРµР· gateway РЅРµ РїСѓР±Р»РёРєСѓРµС‚СЃСЏ. */
@RestController
@RequestMapping("/internal")
public class InternalController {
    private final CatalogService service; private final DestinationRepository destinations;

    public InternalController(CatalogService service, DestinationRepository destinations) { this.service = service; this.destinations = destinations; }

    @PostMapping("/offers/check")
    public List<CheckResult> check(@RequestBody List<CheckItem> items) { return items.stream().map(service::check).toList(); }

    @PostMapping("/offers/candidates")
    public List<CheckResult> candidates(@RequestBody CandidateRequest r) { return service.candidates(r); }

    @GetMapping("/destinations/{id}")
    public DestinationDto destination(@PathVariable long id) {
        Destination d = destinations.findById(id).orElseThrow(() -> ApiException.notFound("РќР°РїСЂР°РІР»РµРЅРёРµ"));
        return new DestinationDto(d.getId(), d.getName(), d.getCountry(), d.getTz());
    }

    @GetMapping("/destinations")
    public List<DestinationDto> destinations() {
        return destinations.findAll().stream().map(d -> new DestinationDto(d.getId(), d.getName(), d.getCountry(), d.getTz())).toList();
    }

    @PostMapping("/holds") @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> hold(@RequestBody HoldRequest r) { return Map.of("holdIds", service.hold(r)); }

    @DeleteMapping("/holds/{bookingRef}")
    public Map<String, Object> release(@PathVariable String bookingRef) { return Map.of("released", service.release(bookingRef)); }

    @PostMapping("/holds/{bookingRef}/convert")
    public Map<String, Object> convert(@PathVariable String bookingRef) { return Map.of("converted", service.convert(bookingRef)); }
}

