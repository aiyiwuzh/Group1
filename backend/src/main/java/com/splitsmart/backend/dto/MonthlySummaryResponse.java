package com.splitsmart.backend.dto;

import java.math.BigDecimal;
import java.util.List;

// Expenses created in one month. Settlements are not spending and are not counted.
public record MonthlySummaryResponse(
        // YYYY-MM
        String month,
        // The time zone used for the month's start and end, e.g. America/Los_Angeles
        String timeZone,
        int expenseCount,
        BigDecimal totalSpending,
        // The current user's own shares of those expenses
        BigDecimal yourShare,
        // Only categories with spending, largest total first
        List<CategoryTotalResponse> categories
) {}
