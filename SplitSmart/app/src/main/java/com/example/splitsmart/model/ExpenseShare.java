package com.example.splitsmart.model;

import java.math.BigDecimal;

public class ExpenseShare {
    private Long userId;
    private String name;
    private BigDecimal amount;
    private boolean payer;

    public Long getUserId() { return userId; }
    public String getName() { return name; }
    public BigDecimal getAmount() { return amount; }
    public boolean isPayer() { return payer; }
}
