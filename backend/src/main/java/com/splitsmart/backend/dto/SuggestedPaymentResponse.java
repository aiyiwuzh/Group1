package com.splitsmart.backend.dto;

import java.math.BigDecimal;

public record SuggestedPaymentResponse(
        Long payerId,
        String payerName,
        Long payeeId,
        String payeeName,
        BigDecimal amount
) {}
