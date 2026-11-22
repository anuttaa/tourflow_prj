package com.tourflow.booking.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "bookings")
@Getter
@Setter
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String ref;

    @Column(name = "user_ref", nullable = false)
    private UUID userRef;

    @Column(name = "user_email")
    private String userEmail;

    @Column(name = "tour_id", nullable = false)
    private Long tourId;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    @Column(name = "tour_title", nullable = false)
    private String tourTitle;

    @Column(name = "destination_name", nullable = false)
    private String destinationName;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private int pax;

    @Column(nullable = false)
    private String status;

    @Column(name = "total_price", nullable = false)
    private BigDecimal totalPrice;

    @Column(nullable = false)
    private BigDecimal penalty = BigDecimal.ZERO;

    @Column(name = "hold_expires_at")
    private Instant holdExpiresAt;

    @Column
    private String comment;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();
}
