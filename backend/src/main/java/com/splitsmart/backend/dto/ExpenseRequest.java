
package com.splitsmart.backend.dto;

import com.splitsmart.backend.entity.ExpenseCategory;
import com.splitsmart.backend.entity.SplitMethod;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

// Used for both creating and editing an expense.
// The date is never part of the request: it is set by the server.
public record ExpenseRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 100, message = "Title cannot exceed 100 characters")
        String title,

        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be greater than 0")
        @Digits(integer = 8, fraction = 2,
                message = "Amount can have at most 2 decimal places")
        BigDecimal amount,

        // Optional; defaults to OTHER
        ExpenseCategory category,

        @NotNull(message = "Payer is required")
        Long payerId,

        @NotEmpty(message = "Participants are required")
        List<Long> participantIds,

        @Size(max = 500, message = "Note cannot exceed 500 characters")
        String note,

        // defaults to EQUAL
        SplitMethod splitMethod,

        // EXACT: dollars per participant id. PERCENTAGE: percent per
        // participant id. Not used for EQUAL.
        Map<Long, BigDecimal> splitValues
) {}
