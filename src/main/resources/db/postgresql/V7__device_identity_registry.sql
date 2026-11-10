-- Additive schema; the next migration backfills and enables maintenance triggers.
create table device_identity (
    device_id varchar(50) primary key,
    owner_kind varchar(16) not null check (owner_kind in ('VEHICLE', 'TRACKER')),
    owner_id varchar(50) not null,
    unique (owner_kind, owner_id)
);
