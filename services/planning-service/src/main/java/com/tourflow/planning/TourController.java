package com.tourflow.planning;

import com.tourflow.planning.Dtos.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** UC-03…UC-06: конструктор тура (менеджер, администратор). */
@RestController
@RequestMapping("/api/v1/tours")
@PreAuthorize("hasAnyRole('TOUR_MANAGER','ADMIN')")
public class TourController {
    private final TourService tours; private final AlternativeService alternatives; private final DemoTourService demo;

    public TourController(TourService tours, AlternativeService alternatives, DemoTourService demo) {
        this.tours = tours; this.alternatives = alternatives; this.demo = demo;
    }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public TourSummary create(@Valid @RequestBody TourForm f) { return tours.create(f); }

    @PostMapping("/demo") @ResponseStatus(HttpStatus.CREATED)
    public List<TourSummary> demo() { return demo.create(); }

    @GetMapping public List<TourSummary> list(@RequestParam(required = false) String status) { return tours.list(status); }

    @GetMapping("/{id}") public TourView get(@PathVariable long id) { return tours.view(id); }

    @PutMapping("/{id}") public Report update(@PathVariable long id, @Valid @RequestBody TourForm f) { return tours.update(id, f); }

    @GetMapping("/{id}/versions") public List<TourSummary> versions(@PathVariable long id) { return tours.versionsOf(id); }

    @PostMapping("/{id}/versions") @ResponseStatus(HttpStatus.CREATED)
    public TourSummary newVersion(@PathVariable long id) { return tours.newVersion(id); }

    @PostMapping("/{id}/segments") @ResponseStatus(HttpStatus.CREATED)
    public SegmentResult addSegment(@PathVariable long id, @Valid @RequestBody SegmentForm f) { return tours.addSegment(id, f); }

    @PatchMapping("/{id}/segments/{segId}")
    public SegmentResult move(@PathVariable long id, @PathVariable long segId, @Valid @RequestBody MoveForm f) { return tours.move(id, segId, f); }

    @DeleteMapping("/{id}/segments/{segId}")
    public Report remove(@PathVariable long id, @PathVariable long segId) { return tours.remove(id, segId); }

    @PostMapping("/{id}/validate")
    public Report validate(@PathVariable long id, @RequestParam(defaultValue = "true") boolean refresh) { return tours.validate(id, refresh); }

    @GetMapping("/{id}/validation")
    public Report validation(@PathVariable long id) { return tours.view(id).report(); }

    @GetMapping("/{id}/price")
    public PriceView price(@PathVariable long id, @RequestParam(required = false) Integer pax) { return tours.priceFor(id, pax); }

    @PostMapping("/{id}/alternatives")
    public AlternativesView suggest(@PathVariable long id, @Valid @RequestBody AltForm f) { return alternatives.suggest(id, f); }

    @PostMapping("/{id}/alternatives/{requestId}/apply")
    public SegmentResult apply(@PathVariable long id, @PathVariable long requestId, @Valid @RequestBody ApplyForm f) {
        return alternatives.apply(id, requestId, f.candidateId());
    }

    @PostMapping("/{id}/publish")
    public TourSummary publish(@PathVariable long id) { return tours.publish(id); }
}
