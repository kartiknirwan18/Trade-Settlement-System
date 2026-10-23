-- =============================================================================
-- Trade Settlement & Invoice Reconciliation System - initial schema
-- Target database: MySQL 8+
--
-- Apply with Flyway (profile "mysql"):  mvn spring-boot:run
-- The schema is validated against the JPA entities at start-up
-- (spring.jpa.hibernate.ddl-auto=validate), so this file and the entity
-- mappings must stay in step.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- users: JWT authentication and role-based authorization
-- ---------------------------------------------------------------------------
CREATE TABLE users (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    username   VARCHAR(60)  NOT NULL,
    email      VARCHAR(150) NOT NULL,
    -- BCrypt hash only; the raw password is never stored
    password   VARCHAR(100) NOT NULL,
    full_name  VARCHAR(150) NULL,
    role       VARCHAR(20)  NOT NULL,
    enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email UNIQUE (email),
    INDEX idx_users_role (role)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------------------------------------
-- trades: physical commodity trades
-- ---------------------------------------------------------------------------
CREATE TABLE trades (
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    trade_reference VARCHAR(40)    NOT NULL,
    commodity       VARCHAR(120)   NOT NULL,
    trade_type      VARCHAR(20)    NOT NULL,
    buyer_name      VARCHAR(150)   NOT NULL,
    seller_name     VARCHAR(150)   NOT NULL,
    quantity        DECIMAL(19, 4) NOT NULL,
    unit_of_measure VARCHAR(20)    NOT NULL,
    unit_price      DECIMAL(19, 4) NOT NULL,
    currency_code   VARCHAR(3)     NOT NULL,
    trade_date      DATE           NOT NULL,
    delivery_date   DATE           NULL,
    status          VARCHAR(20)    NOT NULL,
    notes           VARCHAR(500)   NULL,
    version         BIGINT         NOT NULL DEFAULT 0,
    created_at      DATETIME(6)    NOT NULL,
    updated_at      DATETIME(6)    NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_trades_reference UNIQUE (trade_reference),
    INDEX idx_trades_commodity (commodity),
    INDEX idx_trades_status (status),
    INDEX idx_trades_trade_date (trade_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------------------------------------
-- invoices: invoices raised from completed trades (snapshot of trade values)
-- ---------------------------------------------------------------------------
CREATE TABLE invoices (
    id                    BIGINT         NOT NULL AUTO_INCREMENT,
    invoice_number        VARCHAR(40)    NOT NULL,
    trade_id              BIGINT         NOT NULL,
    trade_reference       VARCHAR(40)    NOT NULL,
    invoice_type          VARCHAR(20)    NOT NULL,
    buyer_name            VARCHAR(150)   NOT NULL,
    seller_name           VARCHAR(150)   NOT NULL,
    invoice_date          DATE           NOT NULL,
    due_date              DATE           NOT NULL,
    commodity_description VARCHAR(200)   NOT NULL,
    quantity              DECIMAL(19, 4) NOT NULL,
    unit_price            DECIMAL(19, 4) NOT NULL,
    subtotal              DECIMAL(19, 4) NOT NULL,
    tax_percentage        DECIMAL(9, 4)  NOT NULL,
    tax_amount            DECIMAL(19, 4) NOT NULL,
    additional_charges    DECIMAL(19, 4) NOT NULL,
    discount_amount       DECIMAL(19, 4) NOT NULL,
    total_amount          DECIMAL(19, 4) NOT NULL,
    amount_paid           DECIMAL(19, 4) NOT NULL DEFAULT 0,
    currency_code         VARCHAR(3)     NOT NULL,
    status                VARCHAR(20)    NOT NULL,
    notes                 VARCHAR(500)   NULL,
    version               BIGINT         NOT NULL DEFAULT 0,
    created_at            DATETIME(6)    NOT NULL,
    updated_at            DATETIME(6)    NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_invoices_number UNIQUE (invoice_number),
    CONSTRAINT fk_invoices_trade FOREIGN KEY (trade_id) REFERENCES trades (id),
    INDEX idx_invoices_trade (trade_id),
    INDEX idx_invoices_status (status),
    INDEX idx_invoices_due_date (due_date),
    INDEX idx_invoices_invoice_date (invoice_date),
    INDEX idx_invoices_currency (currency_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------------------------------------
-- payments: incoming / outgoing settlements against invoices.
-- Rows are never deleted; reversals flip the status to REVERSED.
-- ---------------------------------------------------------------------------
CREATE TABLE payments (
    id                    BIGINT         NOT NULL AUTO_INCREMENT,
    payment_reference     VARCHAR(40)    NOT NULL,
    invoice_id            BIGINT         NOT NULL,
    direction             VARCHAR(20)    NOT NULL,
    payment_date          DATE           NOT NULL,
    amount                DECIMAL(19, 4) NOT NULL,
    currency_code         VARCHAR(3)     NOT NULL,
    payment_method        VARCHAR(20)    NOT NULL,
    transaction_reference VARCHAR(80)    NULL,
    status                VARCHAR(20)    NOT NULL,
    notes                 VARCHAR(500)   NULL,
    reversal_reason       VARCHAR(500)   NULL,
    reversed_at           DATETIME(6)    NULL,
    version               BIGINT         NOT NULL DEFAULT 0,
    created_at            DATETIME(6)    NOT NULL,
    updated_at            DATETIME(6)    NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payments_reference UNIQUE (payment_reference),
    CONSTRAINT fk_payments_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id),
    INDEX idx_payments_invoice (invoice_id),
    INDEX idx_payments_status (status),
    INDEX idx_payments_date (payment_date),
    INDEX idx_payments_currency (currency_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------------------------------------
-- reconciliations: expected vs actual settlement comparison history
-- ---------------------------------------------------------------------------
CREATE TABLE reconciliations (
    id                   BIGINT         NOT NULL AUTO_INCREMENT,
    invoice_id           BIGINT         NOT NULL,
    expected_amount      DECIMAL(19, 4) NOT NULL,
    actual_settled_amount DECIMAL(19, 4) NOT NULL,
    variance_amount      DECIMAL(19, 4) NOT NULL,
    currency_code        VARCHAR(3)     NOT NULL,
    reconciliation_date  DATE           NOT NULL,
    status               VARCHAR(20)    NOT NULL,
    notes                VARCHAR(500)   NULL,
    resolved             BOOLEAN        NOT NULL DEFAULT FALSE,
    resolution_notes     VARCHAR(500)   NULL,
    resolved_by          VARCHAR(80)    NULL,
    resolved_at          DATETIME(6)    NULL,
    created_at           DATETIME(6)    NOT NULL,
    updated_at           DATETIME(6)    NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_reconciliations_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id),
    INDEX idx_reconciliations_invoice (invoice_id),
    INDEX idx_reconciliations_status (status),
    INDEX idx_reconciliations_date (reconciliation_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------------------------------------
-- overdue_alerts: raised by the scheduled monitoring job.
-- uk_alerts_invoice_type is the database-level guarantee that the same
-- overdue condition never produces two alert rows for one invoice.
-- ---------------------------------------------------------------------------
CREATE TABLE overdue_alerts (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    invoice_id      BIGINT       NOT NULL,
    alert_type      VARCHAR(30)  NOT NULL,
    message         VARCHAR(500) NOT NULL,
    trigger_date    DATE         NOT NULL,
    severity        VARCHAR(20)  NOT NULL,
    acknowledged    BOOLEAN      NOT NULL DEFAULT FALSE,
    acknowledged_by VARCHAR(80)  NULL,
    acknowledged_at DATETIME(6)  NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_alerts_invoice_type UNIQUE (invoice_id, alert_type),
    CONSTRAINT fk_alerts_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id),
    INDEX idx_alerts_invoice (invoice_id),
    INDEX idx_alerts_acknowledged (acknowledged),
    INDEX idx_alerts_severity (severity)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------------------------------------
-- audit_logs: append-only trail. The application exposes no update or delete
-- operation for this table and the entity rejects both at the persistence layer.
-- ---------------------------------------------------------------------------
CREATE TABLE audit_logs (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    entity_type     VARCHAR(50)   NOT NULL,
    entity_id       VARCHAR(64)   NULL,
    action          VARCHAR(50)   NOT NULL,
    previous_values VARCHAR(4000) NULL,
    new_values      VARCHAR(4000) NULL,
    performed_by    VARCHAR(80)   NOT NULL,
    performed_at    DATETIME(6)   NOT NULL,
    reason          VARCHAR(500)  NULL,
    PRIMARY KEY (id),
    INDEX idx_audit_entity (entity_type, entity_id),
    INDEX idx_audit_performed_by (performed_by),
    INDEX idx_audit_performed_at (performed_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
