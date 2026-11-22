package com.tourflow.booking;

import com.tourflow.booking.Clients.CatalogClient;
import com.tourflow.booking.Clients.PlanningClient;
import com.tourflow.booking.model.Booking;
import com.tourflow.booking.model.History;
import com.tourflow.booking.model.Item;
import com.tourflow.booking.model.Payment;
import com.tourflow.booking.model.Policy;
import com.tourflow.booking.model.Traveler;
import com.tourflow.booking.repository.BookingRepository;
import com.tourflow.booking.repository.HistoryRepository;
import com.tourflow.booking.repository.ItemRepository;
import com.tourflow.booking.repository.PaymentRepository;
import com.tourflow.booking.repository.PolicyRepository;
import com.tourflow.booking.repository.TravelerRepository;
import com.tourflow.common.dto.Contracts.HoldItem;
import com.tourflow.common.dto.Contracts.HoldRequest;
import com.tourflow.common.dto.Contracts.PublishedSnapshot;
import com.tourflow.common.dto.Contracts.SegmentSnap;
import com.tourflow.common.outbox.OutboxService;
import com.tourflow.common.pricing.PriceCalculator;
import com.tourflow.common.web.ApiException;
import com.tourflow.common.web.CurrentUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * UC-07…UC-09: бронирование с удержанием квот и компенсацией (алгоритм 3),
 * машина состояний заявки.
 */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final BookingRepository bookings;
    private final ItemRepository items;
    private final TravelerRepository travelers;
    private final HistoryRepository history;
    private final PaymentRepository payments;
    private final PolicyRepository policies;
    private final CatalogClient catalog;
    private final PlanningClient planning;
    private final OutboxService outbox;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final BigDecimal margin;
    private final int ttlMinutes;

    public BookingService(
            BookingRepository bookings,
            ItemRepository items,
            TravelerRepository travelers,
            HistoryRepository history,
            PaymentRepository payments,
            PolicyRepository policies,
            CatalogClient catalog,
            PlanningClient planning,
            OutboxService outbox,
            JdbcTemplate jdbc,
            PlatformTransactionManager tm,
            @Value("${app.pricing.margin}") BigDecimal margin,
            @Value("${app.hold-ttl-minutes}") int ttlMinutes
    ) {
        this.bookings = bookings;
        this.items = items;
        this.travelers = travelers;
        this.history = history;
        this.payments = payments;
        this.policies = policies;
        this.catalog = catalog;
        this.planning = planning;
        this.outbox = outbox;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(tm);
        this.margin = margin;
        this.ttlMinutes = ttlMinutes;
    }

    public record TravelerForm(
            String fullName,
            LocalDate birthDate,
            String docNumber,
            String citizenship
    ) {}

    public record CreateForm(
            Long tourId,
            int pax,
            List<TravelerForm> travelers,
            String comment
    ) {}

    public record HistoryView(
            String fromStatus,
            String toStatus,
            UUID changedBy,
            String reason,
            Instant createdAt
    ) {}

    public record BookingView(
            Booking booking,
            List<Item> items,
            List<Traveler> travelers,
            List<HistoryView> history,
            List<Payment> payments
    ) {}

    // ---------- Создание заявки ----------

    public BookingView create(CreateForm f) {
        CurrentUser me = CurrentUser.get();
        PublishedSnapshot snap = planning.snapshot(f.tourId());
        if (!"PUBLISHED".equals(snap.status())) {
            throw ApiException.conflict("Тур недоступен для бронирования");
        }
        if (!snap.startDate().isAfter(LocalDate.now())) {
            throw ApiException.conflict("Тур уже начался или стартует сегодня");
        }
        if (f.pax() < 1 || f.pax() > 50) {
            throw ApiException.invalid("Число участников от 1 до 50");
        }
        if (f.travelers() == null || f.travelers().size() != f.pax()) {
            throw ApiException.invalid("Укажите данные всех " + f.pax() + " участников");
        }
        for (TravelerForm t : f.travelers()) {
            if (t.fullName() == null || t.fullName().isBlank() || t.fullName().length() > 150) {
                throw ApiException.invalid("Укажите ФИО каждого участника (до 150 символов)");
            }
        }

        String ref = "BK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        PriceCalculator.Result price = PriceCalculator.calc(
                snap.segments().stream()
                        .map(s -> new PriceCalculator.Line(s.unitPrice(), s.seasonK(), s.unitsPerPax()))
                        .toList(),
                f.pax(),
                margin
        );

        catalog.hold(new HoldRequest(
                ref,
                ttlMinutes,
                snap.segments().stream()
                        .map(s -> new HoldItem(s.offerRef(), s.dates(), f.pax()))
                        .toList()
        ));

        try {
            Long id = tx.execute(st -> persist(ref, me, snap, f, price));
            return view(id);
        } catch (RuntimeException e) {
            log.warn("Сохранение заявки {} не удалось, компенсация: освобождение квот", ref);
            try {
                catalog.release(ref);
            } catch (RuntimeException ex) {
                log.error("Компенсация не выполнена для {}", ref, ex);
            }
            throw e;
        }
    }

    private Long persist(String ref, CurrentUser me, PublishedSnapshot snap, CreateForm f, PriceCalculator.Result price) {
        Booking b = new Booking();
        b.setRef(ref);
        b.setUserRef(me.id());
        b.setUserEmail(me.email());
        b.setTourId(snap.tourId());
        b.setVersionId(snap.versionId());
        b.setTourTitle(snap.title());
        b.setDestinationName(snap.destinationName());
        b.setStartDate(snap.startDate());
        b.setEndDate(snap.endDate());
        b.setPax(f.pax());
        b.setStatus("PENDING");
        b.setTotalPrice(price.total());
        b.setComment(f.comment());
        b.setHoldExpiresAt(Instant.now().plus(ttlMinutes, ChronoUnit.MINUTES));
        b = bookings.save(b);

        for (SegmentSnap s : snap.segments()) {
            Item i = new Item();
            i.setBookingId(b.getId());
            i.setOfferRef(s.offerRef());
            i.setType(s.type());
            i.setTitle(s.title());
            i.setUnitPrice(s.unitPrice());
            i.setSeasonK(s.seasonK());
            i.setUnitsPerPax(s.unitsPerPax());
            i.setDates(s.dates().stream().map(LocalDate::toString).collect(Collectors.joining(",")));
            i.setLineTotal(s.unitPrice()
                    .multiply(s.seasonK())
                    .multiply(BigDecimal.valueOf((long) s.unitsPerPax() * f.pax()))
                    .setScale(2, RoundingMode.HALF_UP));
            items.save(i);
        }

        for (TravelerForm t : f.travelers()) {
            Traveler x = new Traveler();
            x.setBookingId(b.getId());
            x.setFullName(t.fullName().trim());
            x.setBirthDate(t.birthDate());
            x.setDocNumber(t.docNumber() != null ? t.docNumber().trim() : null);
            x.setCitizenship(t.citizenship() != null ? t.citizenship().trim() : null);
            x.setDocStatus(t.docNumber() != null && !t.docNumber().isBlank() ? "PENDING_VERIFICATION" : "DATA_REQUIRED");
            travelers.save(x);
        }

        logHistory(b, null, "PENDING", me.id(), "Заявка создана, места удержаны до " + b.getHoldExpiresAt());
        outbox.publish("booking.created", b.getRef(), payload(b, null, null));
        return b.getId();
    }

    // ---------- Переходы состояний ----------

    private Map<String, Object> payload(Booking b, String from, String reason) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("bookingId", b.getId());
        m.put("ref", b.getRef());
        m.put("userId", b.getUserRef().toString());
        m.put("userEmail", String.valueOf(b.getUserEmail()));
        m.put("tourTitle", b.getTourTitle());
        m.put("pax", b.getPax());
        m.put("totalPrice", b.getTotalPrice());
        m.put("status", b.getStatus());
        m.put("fromStatus", from == null ? "" : from);
        m.put("reason", reason == null ? "" : reason);
        m.put("holdExpiresAt", b.getHoldExpiresAt() == null ? "" : b.getHoldExpiresAt().toString());
        return m;
    }

    private void logHistory(Booking b, String from, String to, UUID by, String reason) {
        History h = new History();
        h.setBookingId(b.getId());
        h.setFromStatus(from);
        h.setToStatus(to);
        h.setChangedBy(by);
        h.setReason(reason);
        history.save(h);
    }

    private void transition(Booking b, String to, UUID by, String reason) {
        String from = b.getStatus();
        b.setStatus(to);
        b.setUpdatedAt(Instant.now());
        logHistory(b, from, to, by, reason);
        outbox.publish("booking.status.changed", b.getRef(), payload(b, from, reason));
        if ("CANCELLED".equals(to)) {
            outbox.publish("booking.cancelled", b.getRef(), payload(b, from, reason));
        }
    }

    private Booking get(long id) {
        return bookings.findById(id).orElseThrow(() -> ApiException.notFound("Заявка"));
    }

    private void checkAccess(Booking b, CurrentUser me) {
        if (!me.staff() && !b.getUserRef().equals(me.id())) {
            throw ApiException.notFound("Заявка");
        }
    }

    public BookingView confirm(long id) {
        CurrentUser me = CurrentUser.get();
        Booking b = get(id);
        if (!"PENDING".equals(b.getStatus())) {
            throw ApiException.conflict("Подтвердить можно только заявку в статусе PENDING");
        }
        if (b.getHoldExpiresAt() != null && b.getHoldExpiresAt().isBefore(Instant.now())) {
            expireOne(b.getId());
            throw ApiException.conflict("Срок удержания мест истёк, заявка закрыта");
        }
        catalog.convert(b.getRef());
        tx.executeWithoutResult(st -> {
            Booking x = get(id);
            transition(x, "CONFIRMED", me.id(), "Подтверждено менеджером");
            x.setHoldExpiresAt(null);
        });
        return view(id);
    }

    public BookingView reject(long id, String reason) {
        CurrentUser me = CurrentUser.get();
        Booking b = get(id);
        if (!"PENDING".equals(b.getStatus())) {
            throw ApiException.conflict("Отклонить можно только заявку в статусе PENDING");
        }
        catalog.release(b.getRef());
        tx.executeWithoutResult(st -> {
            Booking x = get(id);
            transition(x, "REJECTED", me.id(), reason == null || reason.isBlank() ? "Отклонено менеджером" : reason);
            x.setHoldExpiresAt(null);
        });
        return view(id);
    }

    @Transactional
    public BookingView requestChanges(long id, String comment) {
        CurrentUser me = CurrentUser.get();
        Booking b = get(id);
        if (!"PENDING".equals(b.getStatus())) {
            throw ApiException.conflict("Запросить изменения можно только по заявке в статусе PENDING");
        }
        if (comment == null || comment.isBlank()) {
            throw ApiException.invalid("Опишите, какие изменения нужны");
        }
        logHistory(b, "PENDING", "PENDING", me.id(), "Запрошены изменения: " + comment);
        outbox.publish("booking.status.changed", b.getRef(), payload(b, "PENDING", "Запрошены изменения: " + comment));
        return view(id);
    }

    BigDecimal penaltyFor(Booking b) {
        long days = ChronoUnit.DAYS.between(LocalDate.now(), b.getStartDate());
        for (Policy p : policies.findAllByOrderByDaysBeforeMinDesc()) {
            if (days >= p.getDaysBeforeMin()) {
                return b.getTotalPrice()
                        .multiply(p.getPenaltyPercent())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            }
        }
        return b.getTotalPrice();
    }

    public BookingView cancel(long id, String reason) {
        CurrentUser me = CurrentUser.get();
        Booking b = get(id);
        checkAccess(b, me);
        if (!List.of("PENDING", "CONFIRMED", "PAID").contains(b.getStatus())) {
            throw ApiException.conflict("Заявку в статусе " + b.getStatus() + " отменить нельзя");
        }
        catalog.release(b.getRef());
        tx.executeWithoutResult(st -> {
            Booking x = get(id);
            boolean cancelledByStaff = me.staff();
            // Штраф удерживается ТОЛЬКО если клиент сам отменяет подтверждённую или оплаченную заявку.
            // При отмене со стороны менеджера/туроператора (me.staff()) или по заявке в статусе PENDING штраф = 0, возврат = 100%.
            BigDecimal penalty = (cancelledByStaff || "PENDING".equals(x.getStatus()))
                    ? BigDecimal.ZERO
                    : penaltyFor(x);
            if ("PAID".equals(x.getStatus())) {
                BigDecimal refund = x.getTotalPrice().subtract(penalty).max(BigDecimal.ZERO);
                Payment p = new Payment();
                p.setBookingId(x.getId());
                p.setKind("REFUND");
                p.setAmount(refund);
                p.setStatus("SUCCEEDED");
                p.setExternalId("sbx-" + UUID.randomUUID().toString().substring(0, 8));
                payments.save(p);
            }
            x.setPenalty(penalty);
            x.setHoldExpiresAt(null);
            String defaultReason = cancelledByStaff
                    ? "Отмена туроператором (без штрафа, полный возврат)"
                    : "Отмена заявки клиентом";
            transition(x, "CANCELLED", me.id(), reason == null || reason.isBlank() ? defaultReason : reason);
        });
        return view(id);
    }

    @Transactional
    public BookingView pay(long id) {
        CurrentUser me = CurrentUser.get();
        Booking b = get(id);
        if (!b.getUserRef().equals(me.id())) {
            throw ApiException.notFound("Заявка");
        }
        if (!"CONFIRMED".equals(b.getStatus())) {
            throw ApiException.conflict("Оплатить можно только подтверждённую заявку");
        }
        Payment p = new Payment();
        p.setBookingId(b.getId());
        p.setKind("PAYMENT");
        p.setAmount(b.getTotalPrice());
        p.setStatus("SUCCEEDED");
        p.setExternalId("sbx-" + UUID.randomUUID().toString().substring(0, 8));
        payments.save(p);
        transition(b, "PAID", me.id(), "Оплата (sandbox)");
        return view(id);
    }

    public void expireOne(long id) {
        Booking b = get(id);
        if (!"PENDING".equals(b.getStatus())) {
            return;
        }
        try {
            catalog.release(b.getRef());
        } catch (RuntimeException e) {
            log.warn("Не удалось освободить квоты {}: {}", b.getRef(), e.getMessage());
            return;
        }
        tx.executeWithoutResult(st -> {
            Booking x = get(id);
            if ("PENDING".equals(x.getStatus())) {
                transition(x, "EXPIRED", null, "Истёк срок удержания мест");
                x.setHoldExpiresAt(null);
            }
        });
    }

    public void expireHolds() {
        for (Booking b : bookings.findByStatusAndHoldExpiresAtBefore("PENDING", Instant.now())) {
            expireOne(b.getId());
        }
        for (Booking b : bookings.findByStatusAndEndDateBefore("PAID", LocalDate.now())) {
            tx.executeWithoutResult(st -> transition(get(b.getId()), "COMPLETED", null, "Поездка завершена"));
        }
    }

    @Transactional(readOnly = true)
    public BookingView view(long id) {
        CurrentUser me = CurrentUser.get();
        Booking b = get(id);
        checkAccess(b, me);
        List<HistoryView> h = jdbc.query(
                "select h.from_status, h.to_status, h.changed_by, h.reason, h.created_at "
                        + "from bookings b join booking_status_history h on h.booking_id = b.id "
                        + "where b.id = ? order by h.id",
                (rs, i) -> new HistoryView(
                        rs.getString(1),
                        rs.getString(2),
                        (UUID) rs.getObject(3),
                        rs.getString(4),
                        rs.getTimestamp(5).toInstant()
                ),
                id
        );
        return new BookingView(
                b,
                items.findByBookingId(id),
                travelers.findByBookingId(id),
                h,
                payments.findByBookingIdOrderByIdAsc(id)
        );
    }
}
