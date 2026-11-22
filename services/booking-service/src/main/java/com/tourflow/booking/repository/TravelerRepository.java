package com.tourflow.booking.repository;

import com.tourflow.booking.model.Traveler;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TravelerRepository extends JpaRepository<Traveler, Long> {

    List<Traveler> findByBookingId(Long bookingId);
}
