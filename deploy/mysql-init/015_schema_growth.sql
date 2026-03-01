-- Growth schema (points/levels/leaderboard) in identity DB (community).
-- NOTE: 作为“成长体系”的 SSOT，当前仅落地最小可用：
-- - user.score：累计积分（用于等级与榜单）
-- - user_score_log：事件幂等与积分流水（event_id 唯一）

use community;

create table if not exists user_score_log (
  id bigint auto_increment primary key,
  user_id int not null,
  event_id varchar(64) not null,
  event_type varchar(64) not null,
  delta int not null,
  create_time timestamp null default current_timestamp,
  unique key uk_event_id (event_id),
  key idx_user_time (user_id, create_time)
);

