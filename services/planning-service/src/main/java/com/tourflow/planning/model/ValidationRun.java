package com.tourflow.planning.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "validation_runs")
@Getter
@Setter
public class ValidationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    @Column(name = "run_at")
    private Instant runAt = Instant.now();

    @Column(name = "errors_count", nullable = false)
    private int errorsCount;

    @Column(name = "warnings_count", nullable = false)
    private int warningsCount;

    @Column
    private boolean refreshed;

    @Column(name = "catalog_available")
    private boolean catalogAvailable = true;
}
