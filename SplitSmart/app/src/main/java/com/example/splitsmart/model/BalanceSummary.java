package com.example.splitsmart.model;

import java.math.BigDecimal;
import java.util.List;

public class BalanceSummary {

    private Long groupId;
    private Long userId;
    private BigDecimal totalOwed;
    private BigDecimal totalReceivable;
    private List<PairwiseBalance> balances;

    public Long getGroupId() { return groupId; }
    public Long getUserId() { return userId; }
    public BigDecimal getTotalOwed() { return totalOwed; }
    public BigDecimal getTotalReceivable() { return totalReceivable; }
    public List<PairwiseBalance> getBalances() { return balances; }
}
