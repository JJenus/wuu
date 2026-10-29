-- Durable plan catalog and authenticated enrollment receipts.
-- Paid activation, entitlement grants, consumption ledgers and renewal remain
-- deliberately absent; enrollment alone never grants product access.
create table subscription_plan_versions (
    id uuid not null,
    plan_id uuid not null,
    version bigint not null,
    model varchar(32) not null,
    cadence varchar(16),
    meter varchar(100) not null,
    usage_limit bigint not null,
    amount numeric(38,18) not null,
    currency varchar(3) not null,
    created_at timestamp(6) with time zone not null,
    primary key (id),
    constraint uk_subscription_plan_version unique (plan_id, version),
    constraint ck_subscription_plan_usage_limit check (usage_limit > 0),
    constraint ck_subscription_plan_amount check (amount >= 0)
);

create table subscription_enrollments (
    id uuid not null,
    organization_id bigint not null,
    user_id bigint not null,
    operation_key varchar(200) not null,
    plan_version_id uuid not null,
    status varchar(32) not null,
    remaining_units bigint not null,
    receipt_status varchar(32) not null,
    receipt_remaining_units bigint not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    primary key (id),
    constraint uk_subscription_enrollment_key unique (organization_id, user_id, operation_key),
    constraint fk_subscription_enrollment_plan foreign key (plan_version_id)
        references subscription_plan_versions (id),
    constraint ck_subscription_enrollment_remaining check (remaining_units >= 0),
    constraint ck_subscription_enrollment_receipt check (receipt_remaining_units >= 0)
);

create index idx_subscription_enrollments_owner
    on subscription_enrollments (organization_id, user_id);
