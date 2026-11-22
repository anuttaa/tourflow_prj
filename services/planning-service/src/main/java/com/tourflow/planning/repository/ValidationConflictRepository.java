package com.tourflow.planning.repository;

import com.tourflow.planning.model.ValidationConflict;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ValidationConflictRepository extends JpaRepository<ValidationConflict, Long> {
}
