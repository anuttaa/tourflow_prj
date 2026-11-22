package com.tourflow.booking.repository;

import com.tourflow.booking.model.Item;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ItemRepository extends JpaRepository<Item, Long> {

    List<Item> findByBookingId(Long bookingId);
}
