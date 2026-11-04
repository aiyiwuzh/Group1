package com.example.splitsmart.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class SplitPreviewRequest {
    private final BigDecimal amount;
    private final Long payerId;
    private final List<Long> participantIds;
    private final String splitMethod;
    private final Map<Long, BigDecimal> splitValues;

    public SplitPreviewRequest(BigDecimal amount, Long payerId,
                               List<Long> participantIds, String splitMethod,
                               Map<Long, BigDecimal> splitValues) {
        this.amount = amount;
        this.payerId = payerId;
        this.participantIds = participantIds;
        this.splitMethod = splitMethod;
        this.splitValues = splitValues;
    }
}
