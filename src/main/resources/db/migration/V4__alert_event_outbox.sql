-- Retained source identities prevent replay from recreating committed alerts.
-- Payloads are historical events, so alert deletion does not erase retry/dedup state.
create table alert_event_outbox (
    source_event_id varchar(64) primary key,
    organization_id bigint not null,
    vehicle_id varchar(50) not null,
    alert_id bigint not null,
    payload text not null,
    created_at timestamp with time zone not null,
    published_at timestamp with time zone,
    attempts integer not null default 0,
    next_attempt_at timestamp with time zone not null
);
create index idx_alert_outbox_pending on alert_event_outbox (published_at, next_attempt_at);
