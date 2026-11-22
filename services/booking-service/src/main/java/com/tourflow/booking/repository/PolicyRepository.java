package com.tourflow.booking.repository;

import com.tourflow.booking.model.Policy;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PolicyRepository extends JpaRepository<Policy, Integer> {

    List<Policy> findAllByOrderByDaysBeforeMinDesc();
}
