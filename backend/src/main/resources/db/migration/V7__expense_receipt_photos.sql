CREATE TABLE expense_receipts (
    expense_id BIGINT NOT NULL PRIMARY KEY,
    content_type VARCHAR(100) NOT NULL,
    image_data LONGBLOB NOT NULL,
    CONSTRAINT fk_expense_receipts_expense FOREIGN KEY (expense_id)
        REFERENCES expenses(id) ON DELETE CASCADE
);
