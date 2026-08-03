#!/usr/bin/env bash
set -euo pipefail

# Seed secure local XXL-JOB admin/group/job metadata.

MYSQL_HOST="${MYSQL_HOST:-mysql-primary}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-}"
XXL_JOB_MYSQL_DATABASE="${XXL_JOB_MYSQL_DATABASE:-xxl_job}"

XXL_JOB_ADMIN_USERNAME="${XXL_JOB_ADMIN_USERNAME:-admin}"
XXL_JOB_ADMIN_PASSWORD="${XXL_JOB_ADMIN_PASSWORD:-dev-local-xxl-admin}"
XXL_JOB_EXECUTOR_APPNAME="${XXL_JOB_EXECUTOR_APPNAME:-community-app}"
XXL_JOB_EXECUTOR_TITLE="${XXL_JOB_EXECUTOR_TITLE:-CommunityApp}"
XXL_JOB_AUTHOR="${XXL_JOB_AUTHOR:-community}"
XXL_JOB_ALARM_EMAIL="${XXL_JOB_ALARM_EMAIL:-}"

if [[ -z "${MYSQL_ROOT_PASSWORD}" ]]; then
  echo "[xxl-job-seed] missing env: MYSQL_ROOT_PASSWORD" >&2
  exit 1
fi

if [[ -z "${XXL_JOB_ADMIN_USERNAME}" || -z "${XXL_JOB_ADMIN_PASSWORD}" ]]; then
  echo "[xxl-job-seed] missing env: XXL_JOB_ADMIN_USERNAME / XXL_JOB_ADMIN_PASSWORD" >&2
  exit 1
fi

if [[ -z "${XXL_JOB_EXECUTOR_APPNAME}" ]]; then
  echo "[xxl-job-seed] missing env: XXL_JOB_EXECUTOR_APPNAME" >&2
  exit 1
fi

sql_escape() {
  local value="${1//\\/\\\\}"
  value="${value//\'/\'\'}"
  printf "%s" "${value}"
}

ADMIN_PASSWORD_HASH="$(printf '%s' "${XXL_JOB_ADMIN_PASSWORD}" | sha256sum | awk '{print $1}')"
XXL_JOB_ADMIN_USERNAME_ESCAPED="$(sql_escape "${XXL_JOB_ADMIN_USERNAME}")"
XXL_JOB_EXECUTOR_APPNAME_ESCAPED="$(sql_escape "${XXL_JOB_EXECUTOR_APPNAME}")"
XXL_JOB_EXECUTOR_TITLE_ESCAPED="$(sql_escape "${XXL_JOB_EXECUTOR_TITLE}")"
XXL_JOB_AUTHOR_ESCAPED="$(sql_escape "${XXL_JOB_AUTHOR}")"
XXL_JOB_ALARM_EMAIL_ESCAPED="$(sql_escape "${XXL_JOB_ALARM_EMAIL}")"

echo "[xxl-job-seed] seeding xxl_job metadata..."

mysql --default-character-set=utf8mb4 -h"${MYSQL_HOST}" -P"${MYSQL_PORT}" -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
use \`${XXL_JOB_MYSQL_DATABASE}\`;
set names utf8mb4;

insert ignore into xxl_job_lock(lock_name) values ('schedule_lock');

insert into xxl_job_user(username, password, token, role, permission)
values ('${XXL_JOB_ADMIN_USERNAME_ESCAPED}', '${ADMIN_PASSWORD_HASH}', null, 1, null)
on duplicate key update
  password = values(password),
  token = null,
  role = 1,
  permission = null;

set @executor_app_name := '${XXL_JOB_EXECUTOR_APPNAME_ESCAPED}';
set @executor_title := '${XXL_JOB_EXECUTOR_TITLE_ESCAPED}';
set @job_author := '${XXL_JOB_AUTHOR_ESCAPED}';
set @alarm_email := '${XXL_JOB_ALARM_EMAIL_ESCAPED}';

insert into xxl_job_group(app_name, title, address_type, address_list, update_time)
select @executor_app_name, @executor_title, 0, null, now()
from dual
where not exists (
  select 1 from xxl_job_group where app_name = @executor_app_name
);

update xxl_job_group
set title = @executor_title,
    address_type = 0,
    address_list = null,
    update_time = now()
where app_name = @executor_app_name;

set @job_group_id := (
  select id
  from xxl_job_group
  where app_name = @executor_app_name
  order by id asc
  limit 1
);

create temporary table community_seed_xxl_job (
  executor_handler varchar(255) not null primary key,
  job_desc varchar(255) not null,
  schedule_type varchar(50) not null,
  schedule_conf varchar(128) not null,
  trigger_status tinyint not null,
  executor_route_strategy varchar(50) not null,
  executor_block_strategy varchar(50) not null
);

-- Keep one row per @XxlJob handler; deploy/tests/xxl_job_seed_contract.sh guards the set.
-- XXL_JOB_HANDLER_SEED_BEGIN
insert into community_seed_xxl_job(
  executor_handler,
  job_desc,
  schedule_type,
  schedule_conf,
  trigger_status,
  executor_route_strategy,
  executor_block_strategy
)
values
  ('searchReindex', 'Search Reindex', 'NONE', '', 0, 'FIRST', 'SERIAL_EXECUTION'),
  ('marketWalletActionProcessor', 'Market Wallet Action Processor', 'CRON', '0/5 * * * * ?', 1, 'FIRST', 'SERIAL_EXECUTION'),
  ('marketWalletActionRecovery', 'Market Wallet Action Recovery', 'CRON', '15 0/1 * * * ?', 1, 'FIRST', 'SERIAL_EXECUTION'),
  ('marketOrderAutoConfirm', 'Market Order Auto Confirm', 'CRON', '30 0/1 * * * ?', 1, 'FIRST', 'SERIAL_EXECUTION');
-- XXL_JOB_HANDLER_SEED_END

update xxl_job_info existing_job
inner join community_seed_xxl_job seeded_job
  on binary seeded_job.executor_handler = binary existing_job.executor_handler
set existing_job.job_desc = seeded_job.job_desc,
    existing_job.update_time = now(),
    existing_job.author = @job_author,
    existing_job.alarm_email = @alarm_email,
    existing_job.schedule_type = seeded_job.schedule_type,
    existing_job.schedule_conf = seeded_job.schedule_conf,
    existing_job.misfire_strategy = 'DO_NOTHING',
    existing_job.executor_route_strategy = seeded_job.executor_route_strategy,
    existing_job.executor_param = '',
    existing_job.executor_block_strategy = seeded_job.executor_block_strategy,
    existing_job.executor_timeout = 0,
    existing_job.executor_fail_retry_count = 0,
    existing_job.glue_type = 'BEAN',
    existing_job.glue_source = '',
    existing_job.glue_remark = 'seeded by deploy',
    existing_job.glue_updatetime = now(),
    existing_job.child_jobid = '',
    existing_job.trigger_status = seeded_job.trigger_status,
    existing_job.trigger_last_time = 0,
    existing_job.trigger_next_time = 0
where existing_job.job_group = @job_group_id;

insert into xxl_job_info(
  job_group,
  job_desc,
  add_time,
  update_time,
  author,
  alarm_email,
  schedule_type,
  schedule_conf,
  misfire_strategy,
  executor_route_strategy,
  executor_handler,
  executor_param,
  executor_block_strategy,
  executor_timeout,
  executor_fail_retry_count,
  glue_type,
  glue_source,
  glue_remark,
  glue_updatetime,
  child_jobid,
  trigger_status,
  trigger_last_time,
  trigger_next_time
)
select
  @job_group_id,
  seeded_job.job_desc,
  now(),
  now(),
  @job_author,
  @alarm_email,
  seeded_job.schedule_type,
  seeded_job.schedule_conf,
  'DO_NOTHING',
  seeded_job.executor_route_strategy,
  seeded_job.executor_handler,
  '',
  seeded_job.executor_block_strategy,
  0,
  0,
  'BEAN',
  '',
  'seeded by deploy',
  now(),
  '',
  seeded_job.trigger_status,
  0,
  0
from community_seed_xxl_job seeded_job
where @job_group_id is not null
  and not exists (
    select 1
    from xxl_job_info
    where job_group = @job_group_id
      and binary executor_handler = binary seeded_job.executor_handler
  );
SQL

echo "[xxl-job-seed] done."
