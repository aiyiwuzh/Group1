package com.splitsmart.backend.dto;

import java.math.BigDecimal;
import java.util.List;

public record BalanceSummaryResponse(
        Long groupId,
        Long userId,
        BigDecimal totalOwed,
        BigDecimal totalReceivable,
        List<PairwiseBalanceResponse> balances
) {}
