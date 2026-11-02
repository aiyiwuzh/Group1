
package com.example.splitsmart.model;

import java.math.BigDecimal;
import java.util.List;

// Body for creating or editing an expense. The date is set by the server.
public class ExpenseRequest {

    private String title;
    private BigDecimal amount;
    private String category;
    private Long payerId;
    private List<Long> participantIds;
    private String note;

    public ExpenseRequest(String title, BigDecimal amount, String category,
                          Long payerId, List<Long> participantIds, String note) {
        this.title = title;
        this.amount = amount;
        this.category = category;
        this.payerId = payerId;
        this.participantIds = participantIds;
        this.note = note;
    }
}
