package com.splitsmart.backend.dto;

import com.splitsmart.backend.entity.ExpenseCategory;

import java.math.BigDecimal;
import java.time.Instant;

// One expense or settlement in a group's history.
public record HistoryItemResponse(
        TransactionType type,
        Long id,
        // Expense title, or "<payer> paid <payee>" for a settlement
        String title,
        BigDecimal amount,
        // Null for settlements
        ExpenseCategory category,
        MemberResponse payer,
        // Null for expenses; the member who received a settlement
        MemberResponse payee,
        // Participants in an expense (payer included); 2 for a settlement
        int participantCount,
        // Null for settlements
        String note,
        Instant createdAt,
        // Expenses only: marked as settled by its creator
        boolean settled
) {}
