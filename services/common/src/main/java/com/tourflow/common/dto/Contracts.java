package com.tourflow.common.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Общие контракты межсервисных REST-вызовов (ТЗ 10.2). */
public final class Contracts {
    private Contracts() {}

    public record OfferSnapshot(long offerId, String type, String title, long destinationId, int classLevel, double rating,
                                BigDecimal basePrice, BigDecimal seasonK, int capacity, Instant startUtc, Instant endUtc,
                                String tz, String placeFrom, String placeTo, Double fromLat, Double fromLon,
                                Double toLat, Double toLon, boolean international, Integer checkInFrom, Integer checkOutUntil,
                                Integer openFrom, Integer openTo, List<Integer> openDays) {}

    public record CheckItem(long offerId, List<LocalDate> dates, int qty) {}

    /** offer == null, если опция не найдена или неактивна. */
    public record CheckResult(long offerId, OfferSnapshot offer, int minFree, boolean available) {}

    public record CandidateRequest(List<String> types, long destinationId, Instant windowStart, Instant windowEnd,
                                   int pax, List<LocalDate> dates, Long excludeOfferId, int maxResults) {}

    public record HoldItem(long offerId, List<LocalDate> dates, int qty) {}

    public record HoldRequest(String bookingRef, int ttlMinutes, List<HoldItem> items) {}

    public record DestinationDto(long id, String name, String country, String tz) {}

    public record SegmentSnap(long offerRef, String type, String title, BigDecimal unitPrice, BigDecimal seasonK,
                              int unitsPerPax, List<LocalDate> dates, Instant start, Instant end) {}

    public record PublishedSnapshot(long tourId, long versionId, String title, String destinationName, LocalDate startDate,
                                    LocalDate endDate, int basePax, String status, List<SegmentSnap> segments) {}
}
