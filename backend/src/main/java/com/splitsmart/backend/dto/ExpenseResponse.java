
package com.splitsmart.backend.dto;

import com.splitsmart.backend.entity.ExpenseCategory;
import com.splitsmart.backend.entity.SplitMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ExpenseResponse(
        Long id,
        Long groupId,
        String title,
        BigDecimal amount,
        ExpenseCategory category,
        MemberResponse payer,
        MemberResponse creator,
        List<MemberResponse> participants,
        SplitMethod splitMethod,
        // What each participant owes; always adds up to the amount
        List<ShareResponse> shares,
        String note,
        Instant createdAt,
        Instant updatedAt,
        boolean settled,
        // True when the current user may edit or delete this expense
        boolean canEdit
) {}
