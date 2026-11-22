package com.tourflow.planning.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "alternative_requests")
@Getter
@Setter
public class AlternativeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    @Column(name = "segment_id")
    private Long segmentId;

    @Column(name = "original_offer_ref", nullable = false)
    private Long originalOfferRef;

    @Column(name = "segment_type", nullable = false)
    private String segmentType;

    @Column(nullable = false)
    private String profile;

    @Column(name = "window_delta_min", nullable = false)
    private int windowDeltaMin;

    @Column(name = "requested_by")
    private UUID requestedBy;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();
}
