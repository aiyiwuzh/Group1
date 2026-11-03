package com.splitsmart.backend.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

// The payer comes from the authenticated JWT, so a client cannot record a
// settlement on behalf of another user.
public record SettlementRequest(
        @NotNull(message = "Choose a group member to pay")
        @Positive(message = "The payee must be a valid group member")
        Long payeeId,

        @NotNull(message = "Enter a settlement amount")
        @Positive(message = "Settlement amount must be greater than zero")
        @Digits(integer = 8, fraction = 2,
                message = "Settlement amount may have at most two decimal places")
        BigDecimal amount
) {}
