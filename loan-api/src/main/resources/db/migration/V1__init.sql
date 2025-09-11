-- V1__init.sql: initial schema for loan-api

CREATE TABLE IF NOT EXISTS loan (
    id UUID PRIMARY KEY,
    amount NUMERIC(19, 2) NOT NULL,
    term_months INT NOT NULL,
    interest_rate NUMERIC(7, 4) NOT NULL,
    remaining_balance NUMERIC(19, 2) NOT NULL,
    status VARCHAR(50) NOT NULL,
    simulated_at TIMESTAMP WITH TIME ZONE,
    approved_at TIMESTAMP WITH TIME ZONE,
    contracted_at TIMESTAMP WITH TIME ZONE,
    disbursed_at TIMESTAMP WITH TIME ZONE,
    last_payment_at TIMESTAMP WITH TIME ZONE,
    customer_id TEXT
);

CREATE TABLE IF NOT EXISTS outbox_event (
    id UUID PRIMARY KEY,
    topic TEXT NOT NULL,
    event_type TEXT NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    sent BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_outbox_event_sent_created_at
    ON outbox_event (sent, created_at);

