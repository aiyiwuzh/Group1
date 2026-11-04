package com.example.splitsmart.model;

import java.math.BigDecimal;

public class Settlement {

    private Long id;
    private Long groupId;
    private Member payer;
    private Member payee;
    private BigDecimal amount;
    private String createdAt;
    private String updatedAt;
    private boolean canEdit;

    public Long getId() { return id; }
    public Long getGroupId() { return groupId; }
    public Member getPayer() { return payer; }
    public Member getPayee() { return payee; }
    public BigDecimal getAmount() { return amount; }
    public String getCreatedAt() { return createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public boolean canEdit() { return canEdit; }
}
