package com.tourflow.planning;

import com.tourflow.planning.model.*;
import com.tourflow.planning.engine.ScheduleValidator;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class Dtos {
    private Dtos() {}

    public record TourForm(@NotBlank @Size(max = 200) String title, @NotNull Long destinationId, @NotNull LocalDate startDate,
                           @NotNull LocalDate endDate, @Min(1) @Max(200) int pax, @DecimalMin("0") BigDecimal budget,
                           @Pattern(regexp = "ECONOMY|BALANCE|COMFORT") String profile) {}

    public record SegmentForm(@NotNull Long offerId, Instant start, Instant end, boolean earlyCheckIn) {}
    public record MoveForm(@NotNull Instant start, @NotNull Instant end, Boolean earlyCheckIn) {}
    public record AltForm(@NotNull Long segmentId, @Pattern(regexp = "ECONOMY|BALANCE|COMFORT") String profile,
                          @Min(30) @Max(1440) Integer deltaMinutes, @Min(1) @Max(10) Integer topN) {}
    public record ApplyForm(@NotNull Long candidateId) {}
    public record RuleForm(@NotNull Double value, boolean active) {}

    public record ConflictView(Long id, String rule, String severity, Long segmentA, Long segmentB, String segmentATitle,
                               String segmentBTitle, long violationMin, String hint, String ruleDescription) {}
    public record LinkView(long fromId, long toId, long requiredMin, long slackMin, boolean critical) {}
    public record PriceView(BigDecimal subtotal, BigDecimal discountRate, BigDecimal margin, BigDecimal total, BigDecimal perPerson, int pax) {}

    /** Результат валидации, возвращаемый после каждого изменения маршрута (UC-04). */
    public record Report(Long runId, long errors, long warnings, boolean catalogAvailable, boolean refreshed,
                         List<ConflictView> conflicts, List<LinkView> links, PriceView price, String tourStatus) {}

    public record TourSummary(Long id, String code, String title, String destinationName, String status, Long versionId, int versionNo,
                              LocalDate startDate, LocalDate endDate, int pax, BigDecimal budget, String profile,
                              BigDecimal totalPrice, BigDecimal perPerson, String managerEmail, String tz, Long destinationId) {}

    public record TourView(TourSummary tour, List<TourSegment> segments, Report report) {}

    public record SegmentResult(TourSegment segment, Report report) {}

    public record CandidateView(Long candidateId, int rank, double score, Long offerRef, String type, String title, Instant start, Instant end,
                                String tz, BigDecimal pricePerPax, double rating, int classLevel, long deltaStartMin, BigDecimal deltaPrice,
                                String explanation, java.util.Map<String, Double> contributions) {}

    public record AlternativesView(Long requestId, boolean empty, List<CandidateView> candidates, List<String> hints) {}

    public record PublicTour(Long id, String code, String title, String destinationName, LocalDate startDate, LocalDate endDate, int pax,
                             BigDecimal totalPrice, BigDecimal perPerson) {}

    public record ProgramItem(String type, String title, Instant startUtc, Instant endUtc, String tz, String placeFrom, String placeTo) {}

    public record PublicTourView(PublicTour tour, List<ProgramItem> program) {}

    public static String errorKey(ScheduleValidator.Result r) { return r.errors() + "/" + r.warnings(); }
}
