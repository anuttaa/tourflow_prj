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
@Table(name = "tours")
@Getter
@Setter
public class Tour {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String title;

    @Column(name = "destination_ref", nullable = false)
    private Long destinationRef;

    @Column(name = "destination_name", nullable = false)
    private String destinationName;

    @Column(name = "destination_tz", nullable = false)
    private String destinationTz;

    @Column(name = "manager_ref", nullable = false)
    private UUID managerRef;

    @Column(name = "manager_email")
    private String managerEmail;

    @Column(nullable = false)
    private String status = "DRAFT";

    @Column(name = "current_version_id")
    private Long currentVersionId;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();
}
