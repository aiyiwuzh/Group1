package com.splitsmart.backend.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record SettlementRequest(
        @NotNull(message = "Choose the person who paid")
        @Positive(message = "The payer must be a valid group member")
        Long payerId,

        @NotNull(message = "Choose a group member to pay")
        @Positive(message = "The payee must be a valid group member")
        Long payeeId,

        @NotNull(message = "Enter a settlement amount")
        @Positive(message = "Settlement amount must be greater than zero")
        @Digits(integer = 8, fraction = 2,
                message = "Settlement amount may have at most two decimal places")
        BigDecimal amount,

        @Size(max = 500, message = "Note cannot exceed 500 characters")
        String note
) {}
