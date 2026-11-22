package com.tourflow.catalog.repository;

import com.tourflow.catalog.model.Hold;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HoldRepository extends JpaRepository<Hold, Long> {

    List<Hold> findByBookingRefAndStatusIn(String bookingRef, Collection<String> statuses);
}
