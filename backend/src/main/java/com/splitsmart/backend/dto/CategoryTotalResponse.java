package com.splitsmart.backend.dto;


import java.math.BigDecimal;

// Spending in one category during a month.
public record CategoryTotalResponse(
        String category,
        int expenseCount,
        BigDecimal total
) {}
