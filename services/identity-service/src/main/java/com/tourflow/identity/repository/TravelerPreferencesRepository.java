package com.tourflow.identity.repository;

import com.tourflow.identity.model.TravelerPreferences;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TravelerPreferencesRepository extends JpaRepository<TravelerPreferences, UUID> {
}