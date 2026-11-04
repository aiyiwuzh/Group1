
package com.splitsmart.backend.dto;

import com.splitsmart.backend.entity.SplitMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ExpenseResponse(
        Long id,
        Long groupId,
        String title,
        BigDecimal amount,
        String category,
        MemberResponse payer,
        MemberResponse creator,
        List<MemberResponse> participants,
        SplitMethod splitMethod,
        // What each participant owes; always adds up to the amount
        List<ShareResponse> shares,
        Map<Long, BigDecimal> splitValues,
        String note,
        Instant createdAt,
        Instant updatedAt,
        boolean settled,
        // True when the current user may edit or delete this expense
        boolean canEdit
) {}
