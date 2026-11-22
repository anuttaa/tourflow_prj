package com.tourflow.planning.repository;

import com.tourflow.planning.model.Tour;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TourRepository extends JpaRepository<Tour, Long> {

    List<Tour> findByStatusInOrderByUpdatedAtDesc(Collection<String> statuses);

    Optional<Tour> findByCode(String code);
}
