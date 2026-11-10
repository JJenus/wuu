-- H2 DDL commits implicitly: stop all writers during this migration.
-- A single backfill statement fails atomically on any pre-existing collision;
-- never discard or silently choose one of the conflicting source rows.
insert into device_identity (device_id, owner_kind, owner_id)
select device_id, 'VEHICLE', vehicle_id from vehicles where device_id is not null
union all
select device_id, 'TRACKER', tracker_id from trackers where device_id is not null;

create trigger vehicles_device_identity
after insert, update, delete on vehicles for each row
call "com.surf.tracker.core.infrastructure.migration.DeviceIdentityTriggerV8";

create trigger trackers_device_identity
after insert, update, delete on trackers for each row
call "com.surf.tracker.core.infrastructure.migration.DeviceIdentityTriggerV8";
