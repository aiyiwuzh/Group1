
package com.splitsmart.backend.dto;

import java.math.BigDecimal;

// What one participant owes for an expense.
public record ShareResponse(
        Long userId,
        String name,
        BigDecimal amount,
        // The payer's share is theirs to keep; they do not owe it to anyone
        boolean payer
) {}
