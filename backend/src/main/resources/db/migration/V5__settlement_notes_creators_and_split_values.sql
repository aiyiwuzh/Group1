-- Track who entered each settlement separately from the person who paid.
ALTER TABLE settlements
    ADD COLUMN creator_id BIGINT NULL,
    ADD COLUMN note VARCHAR(500) NULL;

-- Existing settlements were created by their payer in the original design.
UPDATE settlements SET creator_id = payer_id WHERE creator_id IS NULL;

ALTER TABLE settlements
    MODIFY COLUMN creator_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_settlement_creator
        FOREIGN KEY (creator_id) REFERENCES users(id);

-- Preserve the entered percentages so percentage expenses remain editable
-- as percentages instead of silently changing into exact-dollar splits.
CREATE TABLE expense_split_values (
    expense_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    split_value DECIMAL(18, 8) NOT NULL,

    PRIMARY KEY (expense_id, user_id),
    CONSTRAINT chk_split_value_nonnegative CHECK (split_value >= 0),
    CONSTRAINT fk_split_value_expense
        FOREIGN KEY (expense_id) REFERENCES expenses(id) ON DELETE CASCADE,
    CONSTRAINT fk_split_value_user
        FOREIGN KEY (user_id) REFERENCES users(id)
);
