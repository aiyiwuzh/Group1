package com.example.splitsmart.model;

import java.math.BigDecimal;

// Status values: YOU_OWE, OWES_YOU, SETTLED.
public class PairwiseBalance {

    private Long memberId;
    private String memberName;
    private String status;
    private BigDecimal amount;

    public Long getMemberId() { return memberId; }
    public String getMemberName() { return memberName; }
    public String getStatus() { return status; }
    public BigDecimal getAmount() { return amount; }
}
