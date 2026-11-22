package com.tourflow.notification.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "user_contacts")
@Getter
@Setter
public class Contact {

    @Id
    @Column(name = "user_ref")
    private UUID userRef;

    @Column(nullable = false)
    private String email;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();
}
