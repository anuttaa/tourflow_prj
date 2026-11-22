package com.tourflow.planning;

import com.tourflow.common.dto.Contracts.*;
import com.tourflow.planning.model.*;
import com.tourflow.planning.repository.*;
import com.tourflow.common.web.ApiException;
import org.springframework.web.bind.annotation.*;

/** Р’РЅСѓС‚СЂРµРЅРЅРёР№ РєРѕРЅС‚СЂР°РєС‚ planning в†’ booking (РўР— 10.2). */
@RestController
public class InternalController {
    private final TourRepository tours; private final TourVersionRepository versions; private final TourSegmentRepository segments;

    public InternalController(TourRepository tours, TourVersionRepository versions, TourSegmentRepository segments) { this.tours = tours; this.versions = versions; this.segments = segments; }

    @GetMapping("/internal/tours/{id}/published-snapshot")
    public PublishedSnapshot snapshot(@PathVariable long id) {
        Tour t = tours.findById(id).orElseThrow(() -> ApiException.notFound("РўСѓСЂ"));
        TourVersion v = versions.findById(t.getCurrentVersionId()).orElseThrow();
        var segs = segments.findByVersionIdOrderByStartUtcAscIdAsc(v.getId()).stream()
                .map(s -> new SegmentSnap(s.getOfferRef(), s.getType(), s.getTitle(), s.getUnitPrice(), s.getSeasonK(), s.getUnitsPerPax(), TourService.datesOf(s), s.getStartUtc(), s.getEndUtc()))
                .toList();
        return new PublishedSnapshot(t.getId(), v.getId(), t.getTitle(), t.getDestinationName(), v.getStartDate(), v.getEndDate(), v.getPax(), t.getStatus(), segs);
    }
}

