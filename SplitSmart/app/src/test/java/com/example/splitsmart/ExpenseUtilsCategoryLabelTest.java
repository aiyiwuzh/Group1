package com.example.splitsmart;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * White-box tests for Task 5 (Lukas Li): the label shown for a category in the
 * history list and the monthly summary. Covers each branch of categoryLabel.
 */
public class ExpenseUtilsCategoryLabelTest {

    @Test
    public void categoryLabelCoversStandardCustomAndMissingValues() {
        // Standard server values get their display label.
        assertEquals("Food", ExpenseUtils.categoryLabel("FOOD"));
        assertEquals("Transportation", ExpenseUtils.categoryLabel("TRANSPORTATION"));
        assertEquals("Other", ExpenseUtils.categoryLabel("OTHER"));

        // Any spelling of a standard value gets the same label. Before the fix,
        // only "other" worked, because categoryIndex compared case-sensitively and
        // fell back to OTHER; "food" was shown as "food".
        assertEquals("Food", ExpenseUtils.categoryLabel("food"));
        assertEquals("Housing", ExpenseUtils.categoryLabel("Housing"));
        assertEquals("Other", ExpenseUtils.categoryLabel("other"));

        // Custom categories are shown exactly as entered.
        assertEquals("Gym", ExpenseUtils.categoryLabel("Gym"));
        assertEquals("Study supplies", ExpenseUtils.categoryLabel("Study supplies"));

        // A missing category is treated as Other.
        assertEquals("Other", ExpenseUtils.categoryLabel(null));
    }
}
