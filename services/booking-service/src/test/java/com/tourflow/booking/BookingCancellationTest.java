package com.tourflow.booking;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.tourflow.booking.Clients.CatalogClient;
import com.tourflow.booking.Clients.PlanningClient;
import com.tourflow.booking.model.Booking;
import com.tourflow.booking.model.Payment;
import com.tourflow.booking.model.Policy;
import com.tourflow.booking.repository.*;
import com.tourflow.common.outbox.OutboxService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

class BookingCancellationTest {

    private BookingRepository bookings;
    private ItemRepository items;
    private TravelerRepository travelers;
    private HistoryRepository history;
    private PaymentRepository payments;
    private PolicyRepository policies;
    private CatalogClient catalog;
    private PlanningClient planning;
    private OutboxService outbox;
    private JdbcTemplate jdbc;
    private PlatformTransactionManager tm;
    private BookingService service;

    private final UUID clientUuid = UUID.randomUUID();
    private final UUID managerUuid = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        bookings = mock(BookingRepository.class);
        items = mock(ItemRepository.class);
        travelers = mock(TravelerRepository.class);
        history = mock(HistoryRepository.class);
        payments = mock(PaymentRepository.class);
        policies = mock(PolicyRepository.class);
        catalog = mock(CatalogClient.class);
        planning = mock(PlanningClient.class);
        outbox = mock(OutboxService.class);
        jdbc = mock(JdbcTemplate.class);
        tm = mock(PlatformTransactionManager.class);
        when(tm.getTransaction(any())).thenReturn(mock(TransactionStatus.class));

        service = new BookingService(bookings, items, travelers, history, payments, policies,
                catalog, planning, outbox, jdbc, tm, new BigDecimal("0.10"), 30);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(UUID id, String email, String role) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(id.toString());
        when(jwt.getClaimAsString("email")).thenReturn(email);
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(role)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private Booking createSampleBooking(String status, BigDecimal total) {
        Booking b = new Booking();
        b.setId(100L);
        b.setRef("BK-12345678");
        b.setUserRef(clientUuid);
        b.setUserEmail("tourist@test.local");
        b.setTourId(1L);
        b.setVersionId(1L);
        b.setTourTitle("Барселона Тур");
        b.setDestinationName("Барселона");
        b.setStartDate(LocalDate.now().plusDays(5));
        b.setEndDate(LocalDate.now().plusDays(10));
        b.setPax(2);
        b.setStatus(status);
        b.setTotalPrice(total);
        b.setPenalty(BigDecimal.ZERO);
        b.setHoldExpiresAt(null);
        return b;
    }

    @Test
    void managerCancellingPaidBooking_hasZeroPenaltyAndFullRefund() {
        authenticate(managerUuid, "manager@test.local", "ROLE_TOUR_MANAGER");

        Booking b = createSampleBooking("PAID", new BigDecimal("1000.00"));
        when(bookings.findById(100L)).thenReturn(Optional.of(b));
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), anyLong()))
                .thenReturn(Collections.emptyList());

        Policy p = new Policy();
        p.setDaysBeforeMin(3);
        p.setPenaltyPercent(new BigDecimal("50"));
        when(policies.findAllByOrderByDaysBeforeMinDesc()).thenReturn(List.of(p));

        service.cancel(100L, "Рейс отменен авиакомпанией");

        // Verify penalty is ZERO because staff cancelled
        assertEquals(BigDecimal.ZERO, b.getPenalty());
        assertEquals("CANCELLED", b.getStatus());

        // Verify full refund payment was created (1000.00, not 500.00)
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(payments).save(captor.capture());
        Payment refund = captor.getValue();
        assertEquals("REFUND", refund.getKind());
        assertEquals(new BigDecimal("1000.00"), refund.getAmount());
    }

    @Test
    void clientCancellingPaidBooking_chargesPenaltyAccordingToPolicy() {
        authenticate(clientUuid, "tourist@test.local", "ROLE_CLIENT");

        Booking b = createSampleBooking("PAID", new BigDecimal("1000.00"));
        when(bookings.findById(100L)).thenReturn(Optional.of(b));
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), anyLong()))
                .thenReturn(Collections.emptyList());

        Policy p = new Policy();
        p.setDaysBeforeMin(3);
        p.setPenaltyPercent(new BigDecimal("50")); // 50% penalty
        when(policies.findAllByOrderByDaysBeforeMinDesc()).thenReturn(List.of(p));

        service.cancel(100L, "Не могу поехать");

        // Verify penalty is calculated (500.00) for client
        assertEquals(new BigDecimal("500.00"), b.getPenalty());
        assertEquals("CANCELLED", b.getStatus());

        // Verify refund payment was 1000 - 500 = 500.00
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(payments).save(captor.capture());
        Payment refund = captor.getValue();
        assertEquals("REFUND", refund.getKind());
        assertEquals(new BigDecimal("500.00"), refund.getAmount());
    }
}
