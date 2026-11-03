CREATE TABLE settlements (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id BIGINT NOT NULL,
    payer_id BIGINT NOT NULL,
    payee_id BIGINT NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,

    CONSTRAINT chk_settlement_amount_positive
        CHECK (amount > 0),
    CONSTRAINT chk_settlement_distinct_users
        CHECK (payer_id <> payee_id),
    CONSTRAINT fk_settlement_group
        FOREIGN KEY (group_id) REFERENCES `groups`(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_settlement_payer
        FOREIGN KEY (payer_id) REFERENCES users(id),
    CONSTRAINT fk_settlement_payee
        FOREIGN KEY (payee_id) REFERENCES users(id)
);

CREATE INDEX idx_settlements_group_created
    ON settlements (group_id, created_at);
