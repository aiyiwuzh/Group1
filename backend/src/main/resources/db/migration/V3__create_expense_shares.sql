-- How an expense is divided: EQUAL, EXACT or PERCENTAGE.
ALTER TABLE expenses
    ADD COLUMN split_method VARCHAR(20) NOT NULL DEFAULT 'EQUAL',
    ADD CONSTRAINT chk_expense_split_method
        CHECK (split_method IN ('EQUAL', 'EXACT', 'PERCENTAGE'));

-- One row per participant: how much of the expense that person owes.
-- The shares of an expense always add up to expenses.amount (the payer's
-- own share is included, they just do not owe it to anyone).
CREATE TABLE expense_shares (
    expense_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    share_amount DECIMAL(10, 2) NOT NULL,

    PRIMARY KEY (expense_id, user_id),

    CONSTRAINT chk_share_amount_not_negative
        CHECK (share_amount >= 0),

    CONSTRAINT fk_share_expense
        FOREIGN KEY (expense_id) REFERENCES expenses(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_share_user
        FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Expenses created before splitting existed get an equal split. Each person
-- gets the amount divided in whole cents, and the leftover cents go to the
-- participants with the lowest user ids.
INSERT INTO expense_shares (expense_id, user_id, share_amount)
SELECT p.expense_id,
       p.user_id,
       (FLOOR(e.amount * 100 / p.n)
            + CASE WHEN p.rn <= MOD(e.amount * 100, p.n) THEN 1 ELSE 0 END) / 100
FROM (SELECT expense_id,
             user_id,
             ROW_NUMBER() OVER (PARTITION BY expense_id ORDER BY user_id) AS rn,
             COUNT(*) OVER (PARTITION BY expense_id) AS n
      FROM expense_participants) p
JOIN expenses e ON e.id = p.expense_id;
