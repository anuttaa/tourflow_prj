package com.tourflow.planning.repository;

import com.tourflow.planning.model.ValidationRun;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ValidationRunRepository extends JpaRepository<ValidationRun, Long> {

    Optional<ValidationRun> findFirstByVersionIdOrderByIdDesc(Long versionId);
}
