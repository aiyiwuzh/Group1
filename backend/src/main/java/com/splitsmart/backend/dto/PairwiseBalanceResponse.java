package com.splitsmart.backend.dto;

import java.math.BigDecimal;

// Status is YOU_OWE, OWES_YOU, or SETTLED from the authenticated user's view.
public record PairwiseBalanceResponse(
        Long memberId,
        String memberName,
        String status,
        BigDecimal amount
) {}
