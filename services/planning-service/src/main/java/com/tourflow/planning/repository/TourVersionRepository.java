package com.tourflow.planning.repository;

import com.tourflow.planning.model.TourVersion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TourVersionRepository extends JpaRepository<TourVersion, Long> {

    List<TourVersion> findByTourIdOrderByVersionNoDesc(Long tourId);
}
