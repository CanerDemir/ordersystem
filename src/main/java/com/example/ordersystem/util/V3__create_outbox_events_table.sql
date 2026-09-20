CREATE SEQUENCE outbox_event_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE outbox_events (
                               id BIGINT NOT NULL DEFAULT nextval('outbox_event_seq'),
                               event_id UUID NOT NULL,
                               event_type VARCHAR(50) NOT NULL,
                               aggregate_type VARCHAR(50) NOT NULL,
                               aggregate_id BIGINT NOT NULL,
                               payload TEXT NOT NULL,
                               status VARCHAR(20) NOT NULL,
                               created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                               published_at TIMESTAMP WITH TIME ZONE,
                               retry_count INT NOT NULL DEFAULT 0,
                               last_error TEXT,
                               locked_until TIMESTAMP WITH TIME ZONE,
                               CONSTRAINT pk_outbox_events PRIMARY KEY (id),
                               CONSTRAINT uk_outbox_events_event_id UNIQUE (event_id)
);

-- Index for polling pending events chronologically
CREATE INDEX idx_outbox_events_status_created_at ON outbox_events (status, created_at);