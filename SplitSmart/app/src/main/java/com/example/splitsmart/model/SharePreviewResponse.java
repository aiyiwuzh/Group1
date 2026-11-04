package com.example.splitsmart.model;

import java.math.BigDecimal;
import java.util.List;

public class SharePreviewResponse {
    private BigDecimal amount;
    private String splitMethod;
    private List<ShareResponse> shares;

    public BigDecimal getAmount() { return amount; }
    public String getSplitMethod() { return splitMethod; }
    public List<ShareResponse> getShares() { return shares; }
}
