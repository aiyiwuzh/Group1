package com.example.splitsmart;

import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;

/**
 * White-box tests for Task 5 (Lukas Li): the whole-number share of the month
 * shown under each category bar on the monthly summary screen.
 */
public class MonthlySummaryPercentTest {

    private static int percent(String part, String total) {
        return MonthlySummaryActivity.percentOf(
                part == null ? null : new BigDecimal(part),
                total == null ? null : new BigDecimal(total));
    }

    @Test
    public void percentOfRoundsHalfUpAndGuardsEmptyTotals() {
        // Checklist example: $30.00 Food of $50.00 spending.
        assertEquals(60, percent("30.00", "50.00"));
        // 1/3 is 33.33...% (rounds down); 2/3 is 66.66...% (rounds up).
        assertEquals(33, percent("1.00", "3.00"));
        assertEquals(67, percent("2.00", "3.00"));
        // Exactly half a percent rounds up: 0.50 of 100.00 is 0.5%.
        assertEquals(1, percent("0.50", "100.00"));
        // A single category is the whole month.
        assertEquals(100, percent("75.25", "75.25"));

        // An empty month has a $0.00 total; this branch avoids dividing by zero.
        assertEquals(0, percent("0.00", "0.00"));
        // Missing values from the server give 0 instead of crashing.
        assertEquals(0, percent(null, "50.00"));
        assertEquals(0, percent("10.00", null));
    }
}
