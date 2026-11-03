package com.splitsmart.backend.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SplitPreviewRequestValidationTest {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    private Set<ConstraintViolation<SplitPreviewRequest>> validate(
            String amount, Long payerId, List<Long> participantIds) {

        return validator.validate(new SplitPreviewRequest(
                amount == null ? null : new BigDecimal(amount),
                payerId, participantIds, null, null));
    }

    @Test
    void acceptsAValidPreviewRequest() {
        assertTrue(validate("10.00", 1L, List.of(1L, 2L)).isEmpty());
    }

    @Test
    void rejectsZeroNegativeOrOverPreciseAmounts() {
        assertFalse(validate("0", 1L, List.of(1L, 2L)).isEmpty());
        assertFalse(validate("-5.00", 1L, List.of(1L, 2L)).isEmpty());
        assertFalse(validate("10.005", 1L, List.of(1L, 2L)).isEmpty());
    }

    @Test
    void rejectsMissingAmountPayerOrParticipants() {
        assertFalse(validate(null, 1L, List.of(1L, 2L)).isEmpty());
        assertFalse(validate("10.00", null, List.of(1L, 2L)).isEmpty());
        assertFalse(validate("10.00", 1L, List.of()).isEmpty());
        assertFalse(validate("10.00", 1L, null).isEmpty());
    }
}
