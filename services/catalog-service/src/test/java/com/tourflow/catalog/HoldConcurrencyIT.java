package com.tourflow.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tourflow.catalog.model.*;
import com.tourflow.catalog.repository.*;
import com.tourflow.common.dto.Contracts.*;
import com.tourflow.common.web.ApiException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** РРЅС‚РµРіСЂР°С†РёРѕРЅРЅС‹Р№ С‚РµСЃС‚ (РўР— 13): 12 РєР»РёРµРЅС‚РѕРІ РѕРґРЅРѕРІСЂРµРјРµРЅРЅРѕ Р·Р°РЅРёРјР°СЋС‚ РїРѕСЃР»РµРґРЅРёРµ 5 РјРµСЃС‚ вЂ” РїСЂРѕРґР°СЋС‚СЃСЏ СЂРѕРІРЅРѕ 5, РѕСЃС‚Р°С‚РѕРє РЅРµ СѓС…РѕРґРёС‚ РІ РјРёРЅСѓСЃ. Р—Р°РїСѓСЃРє: mvn verify РїСЂРё РЅР°Р»РёС‡РёРё Docker. */
@SpringBootTest(properties = {"app.seed-enabled=false", "spring.kafka.bootstrap-servers=localhost:1", "app.outbox.delay-ms=3600000"})
@Testcontainers(disabledWithoutDocker = true)
class HoldConcurrencyIT {
    @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", pg::getJdbcUrl);
        r.add("spring.datasource.username", pg::getUsername);
        r.add("spring.datasource.password", pg::getPassword);
    }

    @Autowired CatalogService service;
    @Autowired DestinationRepository destinations; @Autowired SupplierRepository suppliers; @Autowired OfferRepository offers; @Autowired AvailabilityRepository availability;

    @Test
    void lastPlacesAreNeverOversold() throws Exception {
        Destination d = new Destination(); d.setName("РўРµСЃС‚"); d.setCountry("РўРµСЃС‚"); d.setTz("Europe/Minsk"); d = destinations.save(d);
        Supplier s = new Supplier(); s.setName("РўРµСЃС‚-РїРѕСЃС‚Р°РІС‰РёРє"); s = suppliers.save(s);
        Offer o = new Offer();
        o.setSupplierId(s.getId()); o.setDestinationId(d.getId()); o.setType("EXCURSION"); o.setTitle("РўРµСЃС‚"); o.setBasePrice(BigDecimal.TEN);
        o.setCapacity(20); o.setTz("Europe/Minsk");
        o = offers.save(o);
        LocalDate day = LocalDate.now().plusDays(10);
        service.setAvailability(o.getId(), day, day, 5);

        long offerId = o.getId();
        ExecutorService pool = Executors.newFixedThreadPool(12);
        AtomicInteger ok = new AtomicInteger(), failed = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> fs = new java.util.ArrayList<>();
        for (int i = 0; i < 12; i++) {
            String ref = "BK-TEST-" + i;
            fs.add(pool.submit(() -> {
                start.await();
                try { service.hold(new HoldRequest(ref, 30, List.of(new HoldItem(offerId, List.of(day), 1)))); ok.incrementAndGet(); }
                catch (ApiException e) { failed.incrementAndGet(); }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : fs) f.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals(5, ok.get());
        assertEquals(7, failed.get());
        assertEquals(0, availability.findByOfferIdAndDate(offerId, day).orElseThrow().getQuotaFree());
        for (int i = 0; i < 12; i++) service.release("BK-TEST-" + i);
        assertEquals(5, availability.findByOfferIdAndDate(offerId, day).orElseThrow().getQuotaFree());
    }
}

