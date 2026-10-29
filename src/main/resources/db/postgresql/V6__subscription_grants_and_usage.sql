-- Explicit non-paid grants only. No payment or recurring renewal transition.
alter table subscription_enrollments add column grant_operation_key varchar(200);
alter table subscription_enrollments add column grant_kind varchar(32);
alter table subscription_enrollments add column grant_administrator_id varchar(40);
alter table subscription_enrollments add column grant_reason varchar(500);
alter table subscription_enrollments add column grant_start timestamp(6) with time zone;
alter table subscription_enrollments add column grant_end timestamp(6) with time zone;
alter table subscription_enrollments add column grant_units bigint;
alter table subscription_enrollments add constraint ck_subscription_grant check (
    (grant_operation_key is null and grant_kind is null and grant_administrator_id is null
      and grant_reason is null and grant_start is null and grant_end is null and grant_units is null)
    or
    (grant_operation_key is not null and grant_kind in ('TRIAL', 'COMPLIMENTARY')
      and grant_kind is not null and grant_administrator_id is not null and grant_reason is not null
      and grant_start is not null and grant_end is not null and grant_units is not null
      and grant_start < grant_end and grant_units > 0)
);

create table subscription_usage_receipts (
    id uuid not null primary key,
    subscription_id uuid not null,
    operation_key varchar(200) not null,
    meter varchar(100) not null,
    units bigint not null,
    recorded_at timestamp(6) with time zone not null,
    remaining_units bigint not null,
    constraint uk_subscription_usage_key unique (subscription_id, operation_key),
    constraint fk_subscription_usage_enrollment foreign key (subscription_id) references subscription_enrollments(id),
    constraint ck_subscription_usage_units check (units > 0 and remaining_units >= 0)
);
