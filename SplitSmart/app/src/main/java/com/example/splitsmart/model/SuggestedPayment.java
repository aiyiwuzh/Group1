package com.example.splitsmart.model;

import java.math.BigDecimal;

public class SuggestedPayment {
    private Long payerId;
    private String payerName;
    private Long payeeId;
    private String payeeName;
    private BigDecimal amount;

    public Long getPayerId() { return payerId; }
    public String getPayerName() { return payerName; }
    public Long getPayeeId() { return payeeId; }
    public String getPayeeName() { return payeeName; }
    public BigDecimal getAmount() { return amount; }
}
