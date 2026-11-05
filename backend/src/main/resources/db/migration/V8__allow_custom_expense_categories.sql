-- V2 limited categories to the built-in values. V6 widened the column for
-- custom labels, so remove the old check constraint to make that supported.
ALTER TABLE expenses
    DROP CHECK chk_expense_category;
