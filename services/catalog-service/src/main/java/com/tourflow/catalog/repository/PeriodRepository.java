package com.tourflow.catalog.repository;

import com.tourflow.catalog.model.PricePeriod;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PeriodRepository extends JpaRepository<PricePeriod, Long> {

    List<PricePeriod> findByOfferId(Long offerId);

    void deleteByOfferId(Long offerId);
}
