package com.splitsmart.backend.dto;

import com.splitsmart.backend.entity.ExpenseCategory;

import java.math.BigDecimal;

// Spending in one category during a month.
public record CategoryTotalResponse(
        ExpenseCategory category,
        int expenseCount,
        BigDecimal total
) {}
