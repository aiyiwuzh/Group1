package com.splitsmart.backend.dto;


import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

// One expense or settlement in a group's history.
public record HistoryItemResponse(
        TransactionType type,
        Long id,
        // Expense title, or "<payer> paid <payee>" for a settlement
        String title,
        BigDecimal amount,
        // Null for settlements
        String category,
        MemberResponse payer,
        // Null for expenses; the member who received a settlement
        MemberResponse payee,
        // Participants in an expense (payer included); 2 for a settlement
        int participantCount,
        // Expense participants; null for settlements
        List<MemberResponse> participants,
        // Null for settlements
        String note,
        Instant createdAt,
        // Expenses only: marked as settled by its creator
        boolean settled
) {}
