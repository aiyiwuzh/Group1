package com.example.splitsmart.model;

import java.math.BigDecimal;
import java.util.List;

// Expenses created in one month. Settlements are not included.
public class MonthlySummary {

    // YYYY-MM
    private String month;
    private String timeZone;
    private int expenseCount;
    private BigDecimal totalSpending;
    private BigDecimal yourShare;
    // Largest total first
    private List<CategoryTotal> categories;

    public String getMonth() { return month; }
    public String getTimeZone() { return timeZone; }
    public int getExpenseCount() { return expenseCount; }
    public BigDecimal getTotalSpending() { return totalSpending; }
    public BigDecimal getYourShare() { return yourShare; }
    public List<CategoryTotal> getCategories() { return categories; }
}
