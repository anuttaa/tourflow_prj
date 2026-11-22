package com.tourflow.common.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Расчёт стоимости (ТЗ 7.4): P = (sum p*q*k) * (1-d) * (1+m), деньги в BigDecimal, HALF_UP до копеек. */
public final class PriceCalculator {
    private PriceCalculator() {}

    /** unitPrice — цена за единицу, unitsPerPax — единиц на человека (ночи для проживания, иначе 1), seasonK — сезонный коэффициент. */
    public record Line(BigDecimal unitPrice, BigDecimal seasonK, int unitsPerPax) {}

    public record Result(BigDecimal subtotal, BigDecimal discountRate, BigDecimal total, BigDecimal perPerson) {}

    public static BigDecimal discountRate(int pax) {
        if (pax >= 10) return new BigDecimal("0.07");
        if (pax >= 5) return new BigDecimal("0.03");
        return BigDecimal.ZERO;
    }

    public static Result calc(List<Line> lines, int pax, BigDecimal margin) {
        BigDecimal sub = BigDecimal.ZERO;
        for (Line l : lines) {
            BigDecimal k = l.seasonK() == null ? BigDecimal.ONE : l.seasonK();
            sub = sub.add(l.unitPrice().multiply(k).multiply(BigDecimal.valueOf((long) l.unitsPerPax() * pax)));
        }
        BigDecimal d = discountRate(pax);
        BigDecimal total = sub.multiply(BigDecimal.ONE.subtract(d)).multiply(BigDecimal.ONE.add(margin))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal per = pax > 0 ? total.divide(BigDecimal.valueOf(pax), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        return new Result(sub.setScale(2, RoundingMode.HALF_UP), d, total, per);
    }
}
