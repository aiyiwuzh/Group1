package com.example.splitsmart.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

// Body for creating or editing an expense. The date is set by the server.
public class ExpenseRequest {

    private String title;
    private BigDecimal amount;
    private String category;
    private Long payerId;
    private List<Long> participantIds;
    private String note;
    private String splitMethod;
    private Map<Long, BigDecimal> splitValues;

    public ExpenseRequest(String title, BigDecimal amount, String category,
                          Long payerId, List<Long> participantIds, String note,
                          String splitMethod, Map<Long, BigDecimal> splitValues) {
        this.title = title;
        this.amount = amount;
        this.category = category;
        this.payerId = payerId;
        this.participantIds = participantIds;
        this.note = note;
        this.splitMethod = splitMethod;
        this.splitValues = splitValues;
    }
}
