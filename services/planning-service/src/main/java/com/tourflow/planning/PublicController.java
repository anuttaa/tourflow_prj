package com.tourflow.planning;

import com.tourflow.common.web.ApiException;
import com.tourflow.planning.Dtos.*;
import com.tourflow.planning.model.*;
import com.tourflow.planning.repository.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

/** FR-19: РІРёС‚СЂРёРЅР° РѕРїСѓР±Р»РёРєРѕРІР°РЅРЅС‹С… С‚СѓСЂРѕРІ (РіРѕСЃС‚СЊ Рё С‚СѓСЂРёСЃС‚): РїРѕРёСЃРє, С„РёР»СЊС‚СЂС‹, СЃРѕСЂС‚РёСЂРѕРІРєР°, СЃС‚СЂР°РЅРёС†С‹. РЎРµР±РµСЃС‚РѕРёРјРѕСЃС‚СЊ Рё РїРѕСЃС‚Р°РІС‰РёРєРё РЅРµ СЂР°СЃРєСЂС‹РІР°СЋС‚СЃСЏ. */
@RestController
@RequestMapping("/api/v1/public/tours")
public class PublicController {
    private final JdbcTemplate jdbc; private final TourRepository tours; private final TourVersionRepository versions; private final TourSegmentRepository segments;

    public PublicController(JdbcTemplate jdbc, TourRepository tours, TourVersionRepository versions, TourSegmentRepository segments) {
        this.jdbc = jdbc; this.tours = tours; this.versions = versions; this.segments = segments;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(required = false) String q, @RequestParam(required = false) String destination,
                                    @RequestParam(required = false) BigDecimal minPrice, @RequestParam(required = false) BigDecimal maxPrice,
                                    @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
                                    @RequestParam(defaultValue = "start,asc") String sort,
                                    @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "12") int size) {
        StringBuilder where = new StringBuilder(" where t.status = 'PUBLISHED'");
        List<Object> args = new ArrayList<>();
        if (q != null && !q.isBlank()) { where.append(" and (lower(t.title) like ? or lower(t.destination_name) like ?)"); String l = "%" + q.toLowerCase() + "%"; args.add(l); args.add(l); }
        if (destination != null && !destination.isBlank()) { where.append(" and lower(t.destination_name) = ?"); args.add(destination.toLowerCase()); }
        if (minPrice != null) { where.append(" and v.total_price / v.pax >= ?"); args.add(minPrice); }
        if (maxPrice != null) { where.append(" and v.total_price / v.pax <= ?"); args.add(maxPrice); }
        if (from != null) { where.append(" and v.start_date >= ?"); args.add(from); }
        if (to != null) { where.append(" and v.start_date <= ?"); args.add(to); }
        String[] s = sort.split(",");
        String col = switch (s[0]) { case "price" -> "v.total_price / v.pax"; case "title" -> "t.title"; default -> "v.start_date"; };
        String dir = s.length > 1 && s[1].equalsIgnoreCase("desc") ? "desc" : "asc";
        int sz = Math.min(Math.max(size, 1), 50), pg = Math.max(page, 0);
        String from_ = " from tours t join tour_versions v on v.id = t.current_version_id" + where;
        Long total = jdbc.queryForObject("select count(*)" + from_, Long.class, args.toArray());
        List<Object> a2 = new ArrayList<>(args); a2.add(sz); a2.add(pg * sz);
        List<PublicTour> items = jdbc.query("select t.id, t.code, t.title, t.destination_name, v.start_date, v.end_date, v.pax, v.total_price, round(v.total_price / v.pax, 2) as per"
                        + from_ + " order by " + col + " " + dir + ", t.id limit ? offset ?",
                (rs, i) -> new PublicTour(rs.getLong("id"), rs.getString("code"), rs.getString("title"), rs.getString("destination_name"), rs.getDate("start_date").toLocalDate(),
                        rs.getDate("end_date").toLocalDate(), rs.getInt("pax"), rs.getBigDecimal("total_price"), rs.getBigDecimal("per")), a2.toArray());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("content", items); out.put("totalElements", total); out.put("page", pg); out.put("size", sz);
        return out;
    }

    @GetMapping("/{key}")
    public PublicTourView get(@PathVariable String key) {
        Tour t = resolvePublished(key);
        TourVersion v = versions.findById(t.getCurrentVersionId()).orElseThrow();
        BigDecimal per = v.getTotalPrice().divide(BigDecimal.valueOf(v.getPax()), 2, java.math.RoundingMode.HALF_UP);
        List<ProgramItem> program = segments.findByVersionIdOrderByStartUtcAscIdAsc(v.getId()).stream()
                .map(x -> new ProgramItem(x.getType(), x.getTitle(), x.getStartUtc(), x.getEndUtc(), x.getTz(), x.getPlaceFrom(), x.getPlaceTo())).toList();
        return new PublicTourView(new PublicTour(t.getId(), t.getCode(), t.getTitle(), t.getDestinationName(), v.getStartDate(), v.getEndDate(), v.getPax(), v.getTotalPrice(), per), program);
    }

    private Tour resolvePublished(String key) {
        var found = key.matches("\\d+") ? tours.findById(Long.parseLong(key)) : tours.findByCode(key);
        return found.filter(x -> x.getStatus().equals("PUBLISHED")).orElseThrow(() -> ApiException.notFound("Тур"));
    }
}

