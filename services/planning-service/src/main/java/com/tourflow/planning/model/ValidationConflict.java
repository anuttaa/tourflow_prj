package com.tourflow.planning.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "validation_conflicts")
@Getter
@Setter
public class ValidationConflict {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "rule_code", nullable = false)
    private String ruleCode;

    @Column(nullable = false)
    private String severity;

    @Column(name = "segment_a_id")
    private Long segmentAId;

    @Column(name = "segment_b_id")
    private Long segmentBId;

    @Column(name = "violation_min", nullable = false)
    private long violationMin;

    @Column
    private String hint;
}
