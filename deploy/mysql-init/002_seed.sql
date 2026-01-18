-- Seed for local dev/demo only.
-- Default user: username=aaa, password=aaa (legacy MD5+salt), status=1 (activated)

insert into user (id, username, password, salt, email, type, status, activation_code, header_url, create_time)
values
  (1, 'aaa', 'be5cdc88ad25c5aa86b9a9e1c3573e79', 'salt', 'aaa@example.com', 0, 1, 'ac', 'http://example.com/a.png', now()),
  (2, 'bbb', 'be5cdc88ad25c5aa86b9a9e1c3573e79', 'salt', 'bbb@example.com', 0, 1, 'ac', 'http://example.com/b.png', now()),
  (3, 'admin', 'be5cdc88ad25c5aa86b9a9e1c3573e79', 'salt', 'admin@example.com', 1, 1, 'ac', 'http://example.com/admin.png', now())
on duplicate key update username = values(username);
