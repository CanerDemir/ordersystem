-- 1. Processed Events Sequence & Table
CREATE SEQUENCE processed_event_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE processed_events (
                                  id BIGINT NOT NULL DEFAULT nextval('processed_event_seq'),
                                  event_id VARCHAR(100) NOT NULL,
                                  processed_at TIMESTAMP WITH TIME ZONE NOT NULL,
                                  CONSTRAINT pk_processed_events PRIMARY KEY (id),
                                  CONSTRAINT uk_processed_events_event_id UNIQUE (event_id)
);

-- 2. Shipments Table - Unique Constraint (1 Order -> 0..1 Shipment Defense)
ALTER TABLE shipments
    ADD CONSTRAINT uk_shipments_order_id UNIQUE (order_id);