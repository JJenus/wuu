-- Backfill and trigger installation must be atomic with respect to all writers.
lock table vehicles, trackers in share row exclusive mode;

insert into device_identity (device_id, owner_kind, owner_id)
select device_id, 'VEHICLE', vehicle_id from vehicles where device_id is not null
union all
select device_id, 'TRACKER', tracker_id from trackers where device_id is not null;

create function maintain_device_identity_v8() returns trigger language plpgsql as $$
declare
    old_owner_id varchar(50);
    new_owner_id varchar(50);
begin
    if TG_OP <> 'INSERT' then
        old_owner_id := to_jsonb(OLD) ->> TG_ARGV[0];
    end if;
    if TG_OP <> 'DELETE' then
        new_owner_id := to_jsonb(NEW) ->> TG_ARGV[0];
    end if;
    if TG_OP = 'UPDATE' then
        if NEW.device_id is not distinct from OLD.device_id
           and new_owner_id is not distinct from old_owner_id then
            return NEW;
        end if;
    end if;
    if TG_OP <> 'INSERT' then
        execute format('delete from %I.device_identity where device_id=$1 and owner_kind=$2 and owner_id=$3', TG_TABLE_SCHEMA)
            using OLD.device_id, TG_ARGV[1], old_owner_id;
    end if;
    if TG_OP <> 'DELETE' then
        if NEW.device_id is not null then
            execute format('insert into %I.device_identity (device_id, owner_kind, owner_id) values ($1,$2,$3)', TG_TABLE_SCHEMA)
                using NEW.device_id, TG_ARGV[1], new_owner_id;
        end if;
        return NEW;
    end if;
    return OLD;
end;
$$;

create trigger vehicles_device_identity after insert or update or delete on vehicles
for each row execute function maintain_device_identity_v8('vehicle_id', 'VEHICLE');

create trigger trackers_device_identity after insert or update or delete on trackers
for each row execute function maintain_device_identity_v8('tracker_id', 'TRACKER');
