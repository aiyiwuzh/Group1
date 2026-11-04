package com.splitsmart.backend.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record SettlementResponse(
        Long id,
        Long groupId,
        MemberResponse payer,
        MemberResponse payee,
        BigDecimal amount,
        String note,
        Instant createdAt,
        Instant updatedAt,
        boolean canEdit
) {}
