-- Identity schema (keep in MYSQL_DATABASE=community for P0).
-- NOTE:
-- - 身份域数据所有权已收敛到 user-service；
-- - auth-service 已不再直连 MySQL（通过调用 user-service internal API 完成鉴权/注册/激活/重置密码等）。

use community;

create table if not exists user (
  id int auto_increment primary key,
  username varchar(255) not null,
  password varchar(255),
  salt varchar(255),
  email varchar(255),
  type int default 0,
  status int default 0,
  activation_code varchar(255),
  header_url varchar(255),
  create_time timestamp null default current_timestamp
);
