package com.tourflow.booking;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Истечение TTL удержания мест и автозавершение оплаченных поездок (ТЗ 7.6, шаг 5). */
@Component
public class HoldExpirationJob {
    private final BookingService service;
    public HoldExpirationJob(BookingService service) { this.service = service; }

    @Scheduled(fixedDelayString = "${app.expire-delay-ms:30000}", initialDelay = 15000)
    public void run() { service.expireHolds(); }
}
