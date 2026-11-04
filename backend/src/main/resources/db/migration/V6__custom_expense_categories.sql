-- Expense categories are user-defined labels, while preserving the existing
-- standard category values already stored as strings.
ALTER TABLE expenses MODIFY COLUMN category VARCHAR(50) NOT NULL;
