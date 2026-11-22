package com.tourflow.planning.repository;

import com.tourflow.planning.model.AlternativeCandidate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AlternativeCandidateRepository extends JpaRepository<AlternativeCandidate, Long> {

    List<AlternativeCandidate> findByRequestIdOrderByRankAsc(Long requestId);
}
