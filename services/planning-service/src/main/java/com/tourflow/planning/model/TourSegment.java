package com.tourflow.planning.model;

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

@Entity
@Table(name = "tour_segments")
@Getter
@Setter
public class TourSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    @Column(name = "offer_ref", nullable = false)
    private Long offerRef;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String title;

    @Column(name = "start_utc", nullable = false)
    private Instant startUtc;

    @Column(name = "end_utc", nullable = false)
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

    @Column(name = "early_check_in")
    private boolean earlyCheckIn;

    @Column(name = "check_in_from")
    private Integer checkInFrom;

    @Column(name = "check_out_until")
    private Integer checkOutUntil;

    @Column(name = "open_from")
    private Integer openFrom;

    @Column(name = "open_to")
    private Integer openTo;

    @Column(name = "open_days")
    private String openDays;

    @Column(name = "class_level", nullable = false)
    private int classLevel = 3;

    @Column(nullable = false)
    private BigDecimal rating = BigDecimal.ZERO;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "season_k", nullable = false)
    private BigDecimal seasonK = BigDecimal.ONE;

    @Column(name = "units_per_pax", nullable = false)
    private int unitsPerPax = 1;

    @Column
    private Integer capacity;

    @Column(name = "free_snapshot")
    private Integer freeSnapshot;

    @Column(nullable = false)
    private boolean stale;

    @Column(nullable = false)
    private int position;
}
