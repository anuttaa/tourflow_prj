package com.tourflow.catalog.repository;

import com.tourflow.catalog.model.OfferSchedule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ScheduleRepository extends JpaRepository<OfferSchedule, Long> {

    List<OfferSchedule> findByOfferId(Long offerId);

    void deleteByOfferId(Long offerId);
}
