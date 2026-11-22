package com.tourflow.planning.repository;

import com.tourflow.planning.model.AlternativeRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AlternativeRequestRepository extends JpaRepository<AlternativeRequest, Long> {
}
