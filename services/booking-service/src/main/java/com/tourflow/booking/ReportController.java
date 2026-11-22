package com.tourflow.booking;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** FR-26: отчёты администратора по заявкам (Q6 выручка, конверсия). */
@RestController
@RequestMapping("/api/v1/reports/admin")
@PreAuthorize("hasRole('ADMIN')")
public class ReportController {
    private final JdbcTemplate jdbc;
    public ReportController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/bookings")
    public Map<String, Object> bookings(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        Timestamp a = Timestamp.from((from == null ? LocalDate.now().minusDays(365) : from).atStartOfDay().toInstant(ZoneOffset.UTC));
        Timestamp b = Timestamp.from((to == null ? LocalDate.now().plusDays(1) : to.plusDays(1)).atStartOfDay().toInstant(ZoneOffset.UTC));
        List<Map<String, Object>> revenue = jdbc.queryForList("""
                select bk.destination_name, to_char(date_trunc('month', p.created_at), 'YYYY-MM') as month,
                       count(distinct bk.id) as bookings,
                       sum(case when p.kind = 'PAYMENT' then p.amount else -p.amount end) as net_revenue
                from bookings bk
                join payments p on p.booking_id = bk.id
                where p.status = 'SUCCEEDED' and p.created_at >= ? and p.created_at < ?
                group by bk.destination_name, date_trunc('month', p.created_at)
                order by month desc, net_revenue desc""", a, b);
        List<Map<String, Object>> funnel = jdbc.queryForList("""
                select status, count(*) as bookings, coalesce(sum(total_price), 0) as amount
                from bookings where created_at >= ? and created_at < ?
                group by status order by bookings desc""", a, b);
        Map<String, Object> conv = jdbc.queryForMap("""
                select count(*) as created,
                       count(*) filter (where status in ('CONFIRMED','PAID','COMPLETED')) as converted,
                       round(100.0 * count(*) filter (where status in ('CONFIRMED','PAID','COMPLETED')) / nullif(count(*), 0), 1) as conversion_pct
                from bookings where created_at >= ? and created_at < ?""", a, b);
        return Map.of("revenue", revenue, "statuses", funnel, "conversion", conv);
    }
}
