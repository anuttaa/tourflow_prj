package com.tourflow.common.pricing;

import static org.junit.jupiter.api.Assertions.*;

import com.tourflow.common.pricing.PriceCalculator.Line;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PriceCalculatorTest {
    static final List<Line> LINES = List.of(new Line(new BigDecimal("100"), BigDecimal.ONE, 1), new Line(new BigDecimal("50"), new BigDecimal("1.2"), 3));

    @Test void noDiscountForSmallGroup() {
        var r = PriceCalculator.calc(LINES, 2, new BigDecimal("0.15"));
        assertEquals(new BigDecimal("560.00"), r.subtotal());
        assertEquals(new BigDecimal("644.00"), r.total());
        assertEquals(new BigDecimal("322.00"), r.perPerson());
    }
    @Test void groupDiscountApplies() {
        assertEquals(new BigDecimal("0.03"), PriceCalculator.discountRate(5));
        assertEquals(new BigDecimal("0.07"), PriceCalculator.discountRate(10));
        assertEquals(new BigDecimal("1561.70"), PriceCalculator.calc(LINES, 5, new BigDecimal("0.15")).total());
    }
    @Test void roundsHalfUpToKopecks() {
        var r = PriceCalculator.calc(List.of(new Line(new BigDecimal("0.01"), BigDecimal.ONE, 1)), 1, new BigDecimal("0.5"));
        assertEquals(new BigDecimal("0.02"), r.total());
    }
    @Test void emptyTourCostsNothing() {
        assertEquals(new BigDecimal("0.00"), PriceCalculator.calc(List.of(), 3, new BigDecimal("0.15")).total());
    }
}
