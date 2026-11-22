package com.tourflow.notification.repository;

import com.tourflow.notification.model.Template;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TemplateRepository extends JpaRepository<Template, Integer> {

    Optional<Template> findByCodeAndActiveTrue(String code);
}
