package com.example.splitsmart.model;

import java.math.BigDecimal;

public class SettlementRequest {

    private final Long payerId;
    private final Long payeeId;
    private final BigDecimal amount;
    private final String note;

    public SettlementRequest(Long payerId, Long payeeId, BigDecimal amount, String note) {
        this.payerId = payerId;
        this.payeeId = payeeId;
        this.amount = amount;
        this.note = note;
    }
}
