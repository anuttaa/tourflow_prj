package com.tourflow.planning;

import com.tourflow.common.web.CurrentUser;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** FR-25, FR-26 (часть planning): агрегирующие запросы Q4, Q5, Q8 по связанным таблицам. */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportService {
    private final JdbcTemplate jdbc;
    public ReportService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static Instant start(LocalDate d) { return (d == null ? LocalDate.now().minusDays(90) : d).atStartOfDay().toInstant(ZoneOffset.UTC); }
    private static Instant end(LocalDate d) { return (d == null ? LocalDate.now().plusDays(1) : d.plusDays(1)).atStartOfDay().toInstant(ZoneOffset.UTC); }

    /** Q4 нагрузка менеджеров и Q5 эффективность автоподбора. */
    @GetMapping("/manager") @PreAuthorize("hasAnyRole('TOUR_MANAGER','ADMIN')")
    public Map<String, Object> manager(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        CurrentUser me = CurrentUser.get();
        Instant a = java.sql.Timestamp.from(start(from)).toInstant(), b = end(to);
        List<Map<String, Object>> load = jdbc.queryForList("""
                select t.manager_email, count(distinct t.id) as tours, count(distinct v.id) as versions,
                       coalesce(sum(r.errors_count), 0) as validation_errors,
                       (select count(*) from alternative_candidates ac
                          join alternative_requests ar on ar.id = ac.request_id
                          join tour_versions v2 on v2.id = ar.version_id
                          join tours t2 on t2.id = v2.tour_id
                         where t2.manager_ref = t.manager_ref and ac.decision = 'ACCEPTED') as accepted_alternatives
                from tours t
                join tour_versions v on v.tour_id = t.id
                left join validation_runs r on r.version_id = v.id
                where t.created_at >= ? and t.created_at < ? and (? or t.manager_ref = ?::uuid)
                group by t.manager_ref, t.manager_email
                order by tours desc""", java.sql.Timestamp.from(a), java.sql.Timestamp.from(b), me.admin(), me.id().toString());
        List<Map<String, Object>> autopick = jdbc.queryForList("""
                select ar.segment_type as type, ar.profile, count(distinct ar.id) as requests,
                       count(ac.id) filter (where ac.decision = 'ACCEPTED') as accepted,
                       round(100.0 * count(ac.id) filter (where ac.decision = 'ACCEPTED') / nullif(count(distinct ar.id), 0), 1) as acceptance_pct
                from alternative_requests ar
                left join alternative_candidates ac on ac.request_id = ar.id
                where ar.created_at >= ? and ar.created_at < ?
                group by ar.segment_type, ar.profile
                order by requests desc""", java.sql.Timestamp.from(a), java.sql.Timestamp.from(b));
        List<Map<String, Object>> byStatus = jdbc.queryForList("select status, count(*) as tours from tours group by status order by status");
        return Map.of("managerLoad", load, "autopick", autopick, "toursByStatus", byStatus);
    }

    /** Q8 топ опций в опубликованных турах. */
    @GetMapping("/admin/planning") @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> admin() {
        List<Map<String, Object>> top = jdbc.queryForList("""
                select s.offer_ref, min(s.title) as title, s.type, count(*) as uses
                from tour_segments s
                join tour_versions v on v.id = s.version_id
                join tours t on t.id = v.tour_id
                where t.status = 'PUBLISHED' and v.id = t.current_version_id
                group by s.offer_ref, s.type
                order by uses desc, title limit 10""");
        return Map.of("topOffers", top);
    }
}
