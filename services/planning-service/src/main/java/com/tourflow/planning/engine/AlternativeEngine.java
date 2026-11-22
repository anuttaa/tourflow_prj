package com.tourflow.planning.engine;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Алгоритм 2 (ТЗ 7.3): автоподбор альтернатив.
 * Шаги: жёсткие ограничения (feasible) → нормировка критериев min–max → S(c)=Σ w_i·f_i(c) → ранжирование top-N с объяснением.
 */
public final class AlternativeEngine {
    private AlternativeEngine() {}

    public record Weights(double price, double time, double rating, double proximity, double similarity) {}

    public static Weights weights(String profile) {
        return switch (profile == null ? "BALANCE" : profile.toUpperCase()) {
            case "ECONOMY" -> new Weights(0.40, 0.20, 0.10, 0.10, 0.20);
            case "COMFORT" -> new Weights(0.10, 0.25, 0.30, 0.15, 0.20);
            default -> new Weights(0.25, 0.25, 0.20, 0.15, 0.15);
        };
    }

    /** Кандидат: сегмент (уже подставленный в расписание), цена за человека, рейтинг, класс. */
    public record Cand(long offerRef, Seg seg, BigDecimal pricePerPax, double rating, int classLevel) {}

    public record Scored(Cand cand, double score, int rank, Map<String, Double> contributions,
                         long deltaStartMin, BigDecimal deltaPrice, String explanation) {}

    /**
     * @param orig        исходный проблемный сегмент
     * @param origClass   класс исходной опции
     * @param origPrice   цена исходной опции за человека
     * @param prev,next   соседние сегменты маршрута (могут быть null) — для критерия близости
     * @param deltaMin    ширина окна по времени (для нормировки критерия «время»)
     * @param feasible    жёсткие ограничения: прогон валидации с подстановкой кандидата + бюджет
     */
    public static List<Scored> rank(List<Cand> candidates, Seg orig, int origClass, BigDecimal origPrice,
                                    Seg prev, Seg next, long deltaMin, Weights w, int topN, Predicate<Cand> feasible) {
        List<Cand> ok = candidates.stream().filter(feasible).toList();
        if (ok.isEmpty()) return List.of();

        double pMin = ok.stream().mapToDouble(c -> c.pricePerPax().doubleValue()).min().orElse(0);
        double pMax = ok.stream().mapToDouble(c -> c.pricePerPax().doubleValue()).max().orElse(0);
        double[] dist = new double[ok.size()];
        double dMax = 0;
        for (int i = 0; i < ok.size(); i++) {
            Seg c = ok.get(i).seg();
            double d1 = prev == null ? 0 : Geo.between(prev, c);
            double d2 = next == null ? 0 : Geo.between(c, next);
            dist[i] = (d1 + d2) / ((prev != null ? 1 : 0) + (next != null ? 1 : 0) == 0 ? 1 : (prev != null ? 1 : 0) + (next != null ? 1 : 0));
            dMax = Math.max(dMax, dist[i]);
        }

        List<Scored> scored = new ArrayList<>();
        for (int i = 0; i < ok.size(); i++) {
            Cand c = ok.get(i);
            double fPrice = pMax == pMin ? 1.0 : 1.0 - (c.pricePerPax().doubleValue() - pMin) / (pMax - pMin);
            long dStart = Duration.between(orig.start(), c.seg().start()).toMinutes();
            double fTime = deltaMin <= 0 ? 1.0 : Math.max(0.0, 1.0 - Math.abs(dStart) / (double) deltaMin);
            double fRating = Math.max(0, Math.min(1, c.rating() / 5.0));
            double fProx = dMax == 0 ? 1.0 : 1.0 - dist[i] / dMax;
            int diff = Math.abs(c.classLevel() - origClass);
            double fSim = (diff == 0 ? 1.0 : diff == 1 ? 0.5 : 0.0) * (c.seg().type() == orig.type() ? 1.0 : 0.5);

            Map<String, Double> contrib = new LinkedHashMap<>();
            contrib.put("price", round(w.price() * fPrice));
            contrib.put("time", round(w.time() * fTime));
            contrib.put("rating", round(w.rating() * fRating));
            contrib.put("proximity", round(w.proximity() * fProx));
            contrib.put("similarity", round(w.similarity() * fSim));
            double s = contrib.values().stream().mapToDouble(Double::doubleValue).sum();

            BigDecimal dPrice = c.pricePerPax().subtract(origPrice);
            String best = contrib.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("price");
            String expl = "Начало " + (dStart >= 0 ? "+" : "") + dStart + " мин, цена " + (dPrice.signum() >= 0 ? "+" : "") + dPrice
                    + " на человека; сильнее всего повлиял критерий «" + label(best) + "»";
            scored.add(new Scored(c, round(s), 0, contrib, dStart, dPrice, expl));
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed().thenComparing(x -> x.cand().pricePerPax()));
        List<Scored> top = new ArrayList<>();
        for (int i = 0; i < Math.min(topN, scored.size()); i++) {
            Scored x = scored.get(i);
            top.add(new Scored(x.cand(), x.score(), i + 1, x.contributions(), x.deltaStartMin(), x.deltaPrice(), x.explanation()));
        }
        return top;
    }

    private static double round(double v) { return Math.round(v * 10000.0) / 10000.0; }

    private static String label(String k) {
        return switch (k) { case "price" -> "цена"; case "time" -> "близость по времени"; case "rating" -> "рейтинг";
            case "proximity" -> "близость к соседним пунктам"; default -> "схожесть класса"; };
    }
}
