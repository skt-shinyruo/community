-- Source: 090_seed_identity.sql
-- --------------------------------------------------------------------
-- Seed for local dev/demo only.
-- Default user: username=aaa, password=aaa (legacy MD5+salt), status=1 (activated)

use community;

insert into user (id, username, password, salt, email, type, status, header_url, create_time)
values
  (x'00000000000070008000000000000001', 'aaa', 'be5cdc88ad25c5aa86b9a9e1c3573e79', 'salt', 'aaa@example.com', 0, 1, 'http://example.com/a.png', now()),
  (x'00000000000070008000000000000002', 'bbb', 'be5cdc88ad25c5aa86b9a9e1c3573e79', 'salt', 'bbb@example.com', 0, 1, 'http://example.com/b.png', now()),
  (x'00000000000070008000000000000003', 'admin', 'be5cdc88ad25c5aa86b9a9e1c3573e79', 'salt', 'admin@example.com', 1, 1, 'http://example.com/admin.png', now())
on duplicate key update username = values(username);
