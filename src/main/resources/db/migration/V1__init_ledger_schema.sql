-- =====================================================================
-- Banking Ledger schema
-- All monetary values are NUMERIC(19,4) (mapped to java.math.BigDecimal).
-- =====================================================================

CREATE TABLE accounts (
    id          UUID          PRIMARY KEY,
    owner_name  VARCHAR(255)  NOT NULL,
    iban        VARCHAR(34)   NOT NULL,
    balance     NUMERIC(19,4) NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uk_accounts_iban UNIQUE (iban),
    -- Last line of defence: the database itself refuses overdrafts.
    CONSTRAINT ck_accounts_balance_non_negative CHECK (balance >= 0)
);

CREATE TABLE transactions (
    id                 UUID          PRIMARY KEY,
    source_account_id  UUID          NOT NULL REFERENCES accounts (id),
    target_account_id  UUID          NOT NULL REFERENCES accounts (id),
    amount             NUMERIC(19,4) NOT NULL,
    status             VARCHAR(16)   NOT NULL,
    reversal_of_id     UUID          NULL REFERENCES transactions (id),
    created_at         TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_transactions_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_transactions_distinct_accounts CHECK (source_account_id <> target_account_id),
    CONSTRAINT ck_transactions_status CHECK (status IN ('COMPLETED', 'FAILED', 'REVERSED'))
);

CREATE INDEX ix_transactions_source_account ON transactions (source_account_id);
CREATE INDEX ix_transactions_target_account ON transactions (target_account_id);
-- A transaction can be reversed at most once.
CREATE UNIQUE INDEX uk_transactions_reversal_of ON transactions (reversal_of_id) WHERE reversal_of_id IS NOT NULL;

CREATE TABLE idempotency_records (
    idempotency_key   VARCHAR(255) PRIMARY KEY,
    request_hash      VARCHAR(64)  NOT NULL,
    response_payload  TEXT         NULL,
    status_code       INTEGER      NULL,
    created_at        TIMESTAMPTZ  NOT NULL
);

CREATE TABLE audit_logs (
    id                BIGSERIAL     PRIMARY KEY,
    transaction_id    UUID          NOT NULL REFERENCES transactions (id),
    account_id        UUID          NOT NULL REFERENCES accounts (id),
    event_type        VARCHAR(32)   NOT NULL,
    previous_balance  NUMERIC(19,4) NOT NULL,
    new_balance       NUMERIC(19,4) NOT NULL,
    created_at        TIMESTAMPTZ   NOT NULL
);

CREATE INDEX ix_audit_logs_transaction ON audit_logs (transaction_id);
CREATE INDEX ix_audit_logs_account ON audit_logs (account_id);

-- audit_logs is append-only: reject any UPDATE or DELETE at the database level.
CREATE FUNCTION audit_logs_reject_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_logs is append-only: % is not allowed', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_logs_append_only
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION audit_logs_reject_mutation();
