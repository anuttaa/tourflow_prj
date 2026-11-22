package com.tourflow.booking;

import com.tourflow.booking.BookingService.BookingView;
import com.tourflow.booking.BookingService.CreateForm;
import com.tourflow.booking.model.Booking;
import com.tourflow.booking.repository.BookingRepository;
import com.tourflow.common.web.CurrentUser;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bookings")
@Validated
public class BookingController {

    private final BookingService service;
    private final BookingRepository repo;

    public BookingController(BookingService service, BookingRepository repo) {
        this.service = service;
        this.repo = repo;
    }

    public record ReasonForm(
            @Size(max = 500) String reason
    ) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CLIENT')")
    public BookingView create(@RequestBody CreateForm f) {
        return service.create(f);
    }

    @GetMapping("/my")
    public Page<Booking> my(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return repo.findByUserRef(
                CurrentUser.get().id(),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "createdAt"))
        );
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TOUR_MANAGER','ADMIN')")
    public Page<Booking> queue(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable p = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        return status == null || status.isBlank() ? repo.findAll(p) : repo.findByStatus(status, p);
    }

    @GetMapping("/{id}")
    public BookingView get(@PathVariable long id) {
        return service.view(id);
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('TOUR_MANAGER','ADMIN')")
    public BookingView confirm(@PathVariable long id) {
        return service.confirm(id);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('TOUR_MANAGER','ADMIN')")
    public BookingView reject(
            @PathVariable long id,
            @RequestBody(required = false) ReasonForm f
    ) {
        return service.reject(id, f == null ? null : f.reason());
    }

    @PostMapping("/{id}/request-changes")
    @PreAuthorize("hasAnyRole('TOUR_MANAGER','ADMIN')")
    public BookingView requestChanges(
            @PathVariable long id,
            @RequestBody ReasonForm f
    ) {
        return service.requestChanges(id, f.reason());
    }

    @PostMapping("/{id}/cancel")
    public BookingView cancel(
            @PathVariable long id,
            @RequestBody(required = false) ReasonForm f
    ) {
        return service.cancel(id, f == null ? null : f.reason());
    }

    @PostMapping("/{id}/pay")
    @PreAuthorize("hasRole('CLIENT')")
    public BookingView pay(@PathVariable long id) {
        return service.pay(id);
    }
}
