package com.tourflow.booking.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "booking_items")
@Getter
@Setter
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "offer_ref", nullable = false)
    private Long offerRef;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String title;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "season_k", nullable = false)
    private BigDecimal seasonK;

    @Column(name = "units_per_pax", nullable = false)
    private int unitsPerPax;

    @Column(nullable = false)
    private String dates;

    @Column(name = "line_total", nullable = false)
    private BigDecimal lineTotal;
}
