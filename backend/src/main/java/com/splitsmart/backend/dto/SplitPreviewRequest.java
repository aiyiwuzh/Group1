
package com.splitsmart.backend.dto;

import com.splitsmart.backend.entity.SplitMethod;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

// Asks what each person would owe, before the expense is saved.
public record SplitPreviewRequest(
        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be greater than 0")
        @Digits(integer = 8, fraction = 2,
                message = "Amount can have at most 2 decimal places")
        BigDecimal amount,

        @NotNull(message = "Payer is required")
        Long payerId,

        @NotEmpty(message = "Participants are required")
        List<Long> participantIds,

        // Optional; defaults to EQUAL
        SplitMethod splitMethod,

        // EXACT: dollars per participant id. PERCENTAGE: percent per
        // participant id. Not used for EQUAL.
        Map<Long, BigDecimal> splitValues
) {}
