package com.tourflow.catalog.repository;

import com.tourflow.catalog.model.Availability;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AvailabilityRepository extends JpaRepository<Availability, Long> {

    Optional<Availability> findByOfferIdAndDate(Long offerId, LocalDate date);

    List<Availability> findByOfferIdAndDateBetweenOrderByDate(Long offerId, LocalDate from, LocalDate to);

    List<Availability> findByOfferIdAndDateIn(Long offerId, Collection<LocalDate> dates);

    @Modifying
    @Query("update Availability a set a.quotaFree = a.quotaFree - :q, a.version = a.version + 1 "
            + "where a.offerId = :o and a.date = :d and a.quotaFree >= :q")
    int decrement(@Param("o") Long offerId, @Param("d") LocalDate date, @Param("q") int qty);

    @Modifying
    @Query("update Availability a set a.quotaFree = case when a.quotaFree + :q > a.quotaTotal then a.quotaTotal else a.quotaFree + :q end, "
            + "a.version = a.version + 1 where a.offerId = :o and a.date = :d")
    int increment(@Param("o") Long offerId, @Param("d") LocalDate date, @Param("q") int qty);
}
