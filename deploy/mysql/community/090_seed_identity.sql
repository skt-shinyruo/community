-- Source: 090_seed_identity.sql
-- --------------------------------------------------------------------
-- Seed for local dev/demo only.
-- Default user: username=aaa, password=aaa, status=1 (activated)

use community;

insert into user (id, username, password, salt, email, type, status, header_url, create_time)
values
  (x'00000000000070008000000000000001', 'aaa', '$2b$10$3.6YcPCzcRJuAETAOx1cWeYxrQ3BEp1iWUNUEfaD8h4p.F4f3LzIq', '', 'aaa@example.com', 0, 1, 'http://example.com/a.png', now()),
  (x'00000000000070008000000000000002', 'bbb', '$2b$10$3.6YcPCzcRJuAETAOx1cWeYxrQ3BEp1iWUNUEfaD8h4p.F4f3LzIq', '', 'bbb@example.com', 0, 1, 'http://example.com/b.png', now()),
  (x'00000000000070008000000000000003', 'admin', '$2b$10$3.6YcPCzcRJuAETAOx1cWeYxrQ3BEp1iWUNUEfaD8h4p.F4f3LzIq', '', 'admin@example.com', 1, 1, 'http://example.com/admin.png', now())
on duplicate key update
  username = values(username),
  password = values(password),
  salt = values(salt),
  email = values(email),
  type = values(type),
  status = values(status),
  header_url = values(header_url);
