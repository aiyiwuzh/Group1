CREATE TABLE expenses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    category VARCHAR(20) NOT NULL DEFAULT 'OTHER',
    payer_id BIGINT NOT NULL,
    creator_id BIGINT NOT NULL,
    note VARCHAR(500),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,

    -- Set by Balances & Settlements when a settlement covers this expense.
    -- Settled expenses cannot be edited or deleted.
    settled BIT(1) NOT NULL DEFAULT b'0',

    CONSTRAINT chk_expense_amount_positive
        CHECK (amount > 0),

    CONSTRAINT chk_expense_category
        CHECK (category IN ('FOOD', 'HOUSING', 'TRANSPORTATION',
                            'ENTERTAINMENT', 'OTHER')),

    CONSTRAINT fk_expense_group
        FOREIGN KEY (group_id) REFERENCES `groups`(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_expense_payer
        FOREIGN KEY (payer_id) REFERENCES users(id),

    CONSTRAINT fk_expense_creator
        FOREIGN KEY (creator_id) REFERENCES users(id)
);

CREATE INDEX idx_expenses_group_created
    ON expenses (group_id, created_at);

CREATE TABLE expense_participants (
    expense_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,

    PRIMARY KEY (expense_id, user_id),

    CONSTRAINT fk_participant_expense
        FOREIGN KEY (expense_id) REFERENCES expenses(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_participant_user
        FOREIGN KEY (user_id) REFERENCES users(id)
);
