-- Optional local development data. This location is not loaded by the production runner.
-- Source: 090_seed_identity.sql
-- --------------------------------------------------------------------
-- Seed for local dev/demo only.
-- Default user: username=aaa, password=aaa, status=1 (activated)


insert into user (id, username, password, salt, email, type, status, header_url, create_time, policy_version)
values
  (x'00000000000070008000000000000001', 'aaa', '$2b$10$3.6YcPCzcRJuAETAOx1cWeYxrQ3BEp1iWUNUEfaD8h4p.F4f3LzIq', '', 'aaa@example.com', 0, 1, 'http://example.com/a.png', now(), 1),
  (x'00000000000070008000000000000002', 'bbb', '$2b$10$3.6YcPCzcRJuAETAOx1cWeYxrQ3BEp1iWUNUEfaD8h4p.F4f3LzIq', '', 'bbb@example.com', 0, 1, 'http://example.com/b.png', now(), 2),
  (x'00000000000070008000000000000003', 'admin', '$2b$10$3.6YcPCzcRJuAETAOx1cWeYxrQ3BEp1iWUNUEfaD8h4p.F4f3LzIq', '', 'admin@example.com', 1, 1, 'http://example.com/admin.png', now(), 3)
on duplicate key update
  username = values(username),
  password = values(password),
  salt = values(salt),
  email = values(email),
  type = values(type),
  status = values(status),
  header_url = values(header_url),
  policy_version = values(policy_version);

insert into user_policy_version_counter(id, current_version)
values (1, 3)
on duplicate key update current_version = greatest(current_version, values(current_version));
