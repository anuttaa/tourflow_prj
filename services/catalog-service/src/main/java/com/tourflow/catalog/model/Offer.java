package com.tourflow.catalog.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "offers")
@Getter
@Setter
public class Offer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(name = "destination_id", nullable = false)
    private Long destinationId;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String title;

    @Column
    private String description;

    @Column(name = "class_level", nullable = false)
    private int classLevel = 3;

    @Column(name = "base_price", nullable = false)
    private BigDecimal basePrice;

    @Column(nullable = false)
    private int capacity;

    @Column(name = "start_utc")
    private Instant startUtc;

    @Column(name = "end_utc")
    private Instant endUtc;

    @Column(nullable = false)
    private String tz;

    @Column(name = "place_from")
    private String placeFrom;

    @Column(name = "place_to")
    private String placeTo;

    @Column(name = "from_lat")
    private Double fromLat;

    @Column(name = "from_lon")
    private Double fromLon;

    @Column(name = "to_lat")
    private Double toLat;

    @Column(name = "to_lon")
    private Double toLon;

    @Column(nullable = false)
    private boolean international;

    @Column(name = "check_in_from")
    private Integer checkInFrom;

    @Column(name = "check_out_until")
    private Integer checkOutUntil;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String attrs = "{}";

    @Column(name = "rating_avg", nullable = false)
    private BigDecimal ratingAvg = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();
}
