package com.tourflow.booking.repository;

import com.tourflow.booking.model.Booking;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    Page<Booking> findByUserRef(UUID userRef, Pageable pageable);

    Page<Booking> findByStatus(String status, Pageable pageable);

    List<Booking> findByStatusAndHoldExpiresAtBefore(String status, Instant holdExpiresAt);

    List<Booking> findByStatusAndEndDateBefore(String status, LocalDate endDate);
}
