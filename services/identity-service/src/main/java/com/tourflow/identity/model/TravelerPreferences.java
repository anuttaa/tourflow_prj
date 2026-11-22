package com.tourflow.identity.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "traveler_preferences")
@Getter
@Setter
public class TravelerPreferences {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column
    private BigDecimal budget;

    @Column(name = "class_level")
    private Integer classLevel;

    @Column
    private String interests;

    @Column(nullable = false)
    private String profile = "BALANCE";
}
