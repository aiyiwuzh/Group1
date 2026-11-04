
package com.example.splitsmart.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class Expense {

    private Long id;
    private Long groupId;
    private String title;
    private BigDecimal amount;
    private String category;
    private Member payer;
    private Member creator;
    private List<Member> participants;
    private String note;
    private String createdAt;
    private String updatedAt;
    private boolean settled;
    private boolean canEdit;
    private String splitMethod;
    private List<ExpenseShare> shares;
    private Map<Long, BigDecimal> splitValues;

    public Long getId() {
        return id;
    }

    public Long getGroupId() {
        return groupId;
    }

    public String getTitle() {
        return title;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCategory() {
        return category;
    }

    public Member getPayer() {
        return payer;
    }

    public Member getCreator() {
        return creator;
    }

    public List<Member> getParticipants() {
        return participants;
    }

    public String getNote() {
        return note;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public boolean isSettled() {
        return settled;
    }

    public boolean canEdit() {
        return canEdit;
    }

    public String getSplitMethod() { return splitMethod; }

    public List<ExpenseShare> getShares() { return shares; }
    public Map<Long, BigDecimal> getSplitValues() { return splitValues; }
}
