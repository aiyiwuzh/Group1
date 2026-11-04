package com.example.splitsmart.model;

import java.math.BigDecimal;

// One row of a group's history: an expense or a settlement.
public class HistoryItem {

    public static final String TYPE_EXPENSE = "EXPENSE";
    public static final String TYPE_SETTLEMENT = "SETTLEMENT";

    private String type;
    private Long id;
    private String title;
    private BigDecimal amount;
    // Null for settlements
    private String category;
    private Member payer;
    // Null for expenses
    private Member payee;
    private int participantCount;
    private String note;
    private String createdAt;
    private boolean settled;

    public String getType() { return type; }
    public Long getId() { return id; }
    public String getTitle() { return title; }
    public BigDecimal getAmount() { return amount; }
    public String getCategory() { return category; }
    public Member getPayer() { return payer; }
    public Member getPayee() { return payee; }
    public int getParticipantCount() { return participantCount; }
    public String getNote() { return note; }
    public String getCreatedAt() { return createdAt; }
    public boolean isSettled() { return settled; }

    public boolean isExpense() {
        return TYPE_EXPENSE.equals(type);
    }
}
