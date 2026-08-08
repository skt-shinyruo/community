-- Source: 090_seed_identity.sql
-- --------------------------------------------------------------------
-- Seed for local dev/demo only.
-- Default user: username=aaa, password=aaa, status=1 (activated)

use community;

start transaction;

set @community_seed_identity_collision = (
  select count(*)
  from user
  where ((username = 'aaa' or email = 'aaa@example.com')
           and id <> x'00000000000070008000000000000001')
     or ((username = 'bbb' or email = 'bbb@example.com')
           and id <> x'00000000000070008000000000000002')
     or ((username = 'admin' or email = 'admin@example.com')
           and id <> x'00000000000070008000000000000003')
);
set @community_seed_sql = if(
  @community_seed_identity_collision = 0,
  'do 0',
  'select * from `__community_seed_identity_owned_by_another_user__`'
);
prepare community_seed_statement from @community_seed_sql;
execute community_seed_statement;
deallocate prepare community_seed_statement;

insert into user_policy_version_counter(id, current_version)
values (1, 0)
on duplicate key update current_version = current_version;

select current_version into @community_seed_policy_base
from user_policy_version_counter
where id = 1
for update;

set @community_seed_policy_base = greatest(
  @community_seed_policy_base,
  coalesce((select max(policy_version) from user), 0),
  coalesce((select max(version) from user_policy_version_log), 0)
);

set @community_seed_aaa_existing_policy_version = (
  select users.policy_version
  from user users
  inner join user_policy_version_log history
    on history.version = users.policy_version
   and history.user_id = users.id
   and history.user_exists = 1
   and history.mute_until <=> users.mute_until
   and history.ban_until <=> users.ban_until
  where users.id = x'00000000000070008000000000000001'
);
set @community_seed_bbb_existing_policy_version = (
  select users.policy_version
  from user users
  inner join user_policy_version_log history
    on history.version = users.policy_version
   and history.user_id = users.id
   and history.user_exists = 1
   and history.mute_until <=> users.mute_until
   and history.ban_until <=> users.ban_until
  where users.id = x'00000000000070008000000000000002'
);
set @community_seed_admin_existing_policy_version = (
  select users.policy_version
  from user users
  inner join user_policy_version_log history
    on history.version = users.policy_version
   and history.user_id = users.id
   and history.user_exists = 1
   and history.mute_until <=> users.mute_until
   and history.ban_until <=> users.ban_until
  where users.id = x'00000000000070008000000000000003'
);

set @community_seed_aaa_needs_policy_version = if(
  @community_seed_aaa_existing_policy_version is null, 1, 0
);
set @community_seed_bbb_needs_policy_version = if(
  @community_seed_bbb_existing_policy_version is null, 1, 0
);
set @community_seed_aaa_policy_version = coalesce(
  @community_seed_aaa_existing_policy_version,
  @community_seed_policy_base + 1
);
set @community_seed_bbb_policy_version = coalesce(
  @community_seed_bbb_existing_policy_version,
  @community_seed_policy_base + @community_seed_aaa_needs_policy_version + 1
);
set @community_seed_admin_policy_version = coalesce(
  @community_seed_admin_existing_policy_version,
  @community_seed_policy_base
    + @community_seed_aaa_needs_policy_version
    + @community_seed_bbb_needs_policy_version
    + 1
);

insert into user (
  id, username, password, salt, email, type, status, header_url, create_time, policy_version, security_version
)
values
  (x'00000000000070008000000000000001', 'aaa', '$2b$10$3.6YcPCzcRJuAETAOx1cWeYxrQ3BEp1iWUNUEfaD8h4p.F4f3LzIq', '', 'aaa@example.com', 0, 1, 'http://example.com/a.png', now(), @community_seed_aaa_policy_version, 1),
  (x'00000000000070008000000000000002', 'bbb', '$2b$10$3.6YcPCzcRJuAETAOx1cWeYxrQ3BEp1iWUNUEfaD8h4p.F4f3LzIq', '', 'bbb@example.com', 0, 1, 'http://example.com/b.png', now(), @community_seed_bbb_policy_version, 2),
  (x'00000000000070008000000000000003', 'admin', '$2b$10$3.6YcPCzcRJuAETAOx1cWeYxrQ3BEp1iWUNUEfaD8h4p.F4f3LzIq', '', 'admin@example.com', 1, 1, 'http://example.com/admin.png', now(), @community_seed_admin_policy_version, 3)
on duplicate key update
  username = values(username),
  password = values(password),
  salt = values(salt),
  email = values(email),
  type = values(type),
  status = values(status),
  header_url = values(header_url),
  policy_version = values(policy_version),
  security_version = greatest(security_version, values(security_version));

insert into user_policy_version_log(
  version, user_id, user_exists, mute_until, ban_until, occurred_at
)
select
  policy_version, id, 1, mute_until, ban_until, coalesce(create_time, current_timestamp)
from user
where id in (
  x'00000000000070008000000000000001',
  x'00000000000070008000000000000002',
  x'00000000000070008000000000000003'
)
on duplicate key update version = values(version);

update user_policy_version_counter
set current_version = greatest(
  current_version,
  @community_seed_aaa_policy_version,
  @community_seed_bbb_policy_version,
  @community_seed_admin_policy_version
)
where id = 1;

insert into user_security_version_counter(id, current_version)
values (1, 3)
on duplicate key update current_version = greatest(current_version, values(current_version));

set @community_seed_missing_policy_history = (
  select count(*)
  from user users
  left join user_policy_version_log history
    on history.version = users.policy_version
   and history.user_id = users.id
   and history.user_exists = 1
   and history.mute_until <=> users.mute_until
   and history.ban_until <=> users.ban_until
  where users.id in (
    x'00000000000070008000000000000001',
    x'00000000000070008000000000000002',
    x'00000000000070008000000000000003'
  )
    and history.version is null
);
set @community_seed_sql = if(
  @community_seed_missing_policy_history = 0,
  'do 0',
  'select * from `__community_seed_policy_history_mismatch__`'
);
prepare community_seed_statement from @community_seed_sql;
execute community_seed_statement;
deallocate prepare community_seed_statement;

commit;
