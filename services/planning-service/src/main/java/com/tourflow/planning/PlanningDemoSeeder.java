package com.tourflow.planning;

import com.tourflow.planning.repository.TourRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class PlanningDemoSeeder implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(PlanningDemoSeeder.class);

    private final TourRepository tourRepository;
    private final DemoTourService demoTourService;

    @Value("${app.seed-enabled:true}")
    private boolean seedEnabled;

    public PlanningDemoSeeder(TourRepository tourRepository, DemoTourService demoTourService) {
        this.tourRepository = tourRepository;
        this.demoTourService = demoTourService;
    }

    @Override
    public void run(String... args) {
        if (!seedEnabled) return;
        try {
            if (tourRepository.count() == 0) {
                log.info("Planning database has no tours. Seeding demo tours...");
                demoTourService.create();
                log.info("Demo tours seeded successfully.");
            }
        } catch (Exception e) {
            log.warn("Could not seed demo tours on startup (catalog service may still be starting): {}", e.getMessage());
        }
    }
}
