package com.example.splitsmart.model;

import java.math.BigDecimal;

public class SettlementRequest {

    private final Long payeeId;
    private final BigDecimal amount;

    public SettlementRequest(Long payeeId, BigDecimal amount) {
        this.payeeId = payeeId;
        this.amount = amount;
    }
}
