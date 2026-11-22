package com.tourflow.identity.repository;

import com.tourflow.identity.model.EmailConfirmation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmailConfirmationRepository extends JpaRepository<EmailConfirmation, UUID> {

    Optional<EmailConfirmation> findByTokenHash(String hash);
}