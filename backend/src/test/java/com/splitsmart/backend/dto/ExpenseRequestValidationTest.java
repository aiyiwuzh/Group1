package com.splitsmart.backend.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ExpenseRequestValidationTest {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    private Set<ConstraintViolation<ExpenseRequest>> validate(
            String title, String amount) {

        return validator.validate(new ExpenseRequest(
                title, amount == null ? null : new BigDecimal(amount),
                null, 1L, List.of(1L, 2L), null));
    }

    @Test
    void acceptsAmountWithTwoDecimals() {
        assertTrue(validate("Dinner", "0.01").isEmpty());
        assertTrue(validate("Dinner", "1234.50").isEmpty());
    }

    @Test
    void rejectsZeroOrNegativeAmount() {
        assertFalse(validate("Dinner", "0").isEmpty());
        assertFalse(validate("Dinner", "-5.00").isEmpty());
    }

    @Test
    void rejectsMoreThanTwoDecimals() {
        assertFalse(validate("Dinner", "10.005").isEmpty());
    }

    @Test
    void rejectsMissingTitleOrAmount() {
        assertFalse(validate("   ", "10.00").isEmpty());
        assertFalse(validate("Dinner", null).isEmpty());
    }
}
