package com.tourflow.planning.repository;

import com.tourflow.planning.model.RuleParam;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RuleParamRepository extends JpaRepository<RuleParam, Long> {

    List<RuleParam> findByActiveTrue();

    List<RuleParam> findAllByOrderByCodeAscParamNameAsc();
}
