package com.tourflow.catalog.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "offer_availability")
@Getter
@Setter
public class Availability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "offer_id", nullable = false)
    private Long offerId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "quota_total", nullable = false)
    private int quotaTotal;

    @Column(name = "quota_free", nullable = false)
    private int quotaFree;

    @Column(nullable = false)
    private int version;
}
