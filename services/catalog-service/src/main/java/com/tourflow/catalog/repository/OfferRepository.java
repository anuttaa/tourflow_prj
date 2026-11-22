package com.tourflow.catalog.repository;

import com.tourflow.catalog.model.Offer;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface OfferRepository extends JpaRepository<Offer, Long>, JpaSpecificationExecutor<Offer> {

    List<Offer> findByDestinationIdAndTypeInAndActiveTrue(Long destinationId, Collection<String> types);
}
