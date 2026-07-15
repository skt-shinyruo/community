alter table auth_refresh_token
  add column security_version bigint null after family_id;

insert into auth_refresh_token_family_revocation(family_id, revoked_at)
select distinct family_id, current_timestamp
from auth_refresh_token
where state in ('ACTIVE', 'PENDING_ROTATION')
on duplicate key update
  revoked_at = greatest(auth_refresh_token_family_revocation.revoked_at, values(revoked_at));

update auth_refresh_token
set pending_expires_at = case
      when state in ('ACTIVE', 'PENDING_ROTATION') then null
      else pending_expires_at
    end,
    revoked_at = case
      when state in ('ACTIVE', 'PENDING_ROTATION') then coalesce(revoked_at, current_timestamp)
      else revoked_at
    end,
    state = case
      when state in ('ACTIVE', 'PENDING_ROTATION') then 'REVOKED'
      else state
    end,
    security_version = 0;

alter table auth_refresh_token
  modify column security_version bigint not null;
