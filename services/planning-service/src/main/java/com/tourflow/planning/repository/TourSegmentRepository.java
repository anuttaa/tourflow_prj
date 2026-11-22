package com.tourflow.planning.repository;

import com.tourflow.planning.model.TourSegment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TourSegmentRepository extends JpaRepository<TourSegment, Long> {

    List<TourSegment> findByVersionIdOrderByStartUtcAscIdAsc(Long versionId);

    @Query("select coalesce(max(s.position), 0) from TourSegment s where s.versionId = :v")
    int maxPosition(@Param("v") Long versionId);

    @Modifying
    @Query("update TourSegment s set s.stale = true where s.offerRef = :o")
    int markStale(@Param("o") Long offerRef);
}
