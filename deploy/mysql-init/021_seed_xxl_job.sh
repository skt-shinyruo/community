#!/usr/bin/env bash
set -euo pipefail

# Seed secure local XXL-JOB admin/group/job metadata.
# This script runs inside the MySQL container init directory on first boot.

MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-}"

XXL_JOB_ADMIN_USERNAME="${XXL_JOB_ADMIN_USERNAME:-admin}"
XXL_JOB_ADMIN_PASSWORD="${XXL_JOB_ADMIN_PASSWORD:-dev-local-xxl-admin}"
XXL_JOB_EXECUTOR_APPNAME="${XXL_JOB_EXECUTOR_APPNAME:-community-app}"
XXL_JOB_EXECUTOR_TITLE="${XXL_JOB_EXECUTOR_TITLE:-CommunityApp}"
XXL_JOB_AUTHOR="${XXL_JOB_AUTHOR:-community}"
XXL_JOB_ALARM_EMAIL="${XXL_JOB_ALARM_EMAIL:-}"
XXL_JOB_PENDING_REGISTRATION_CLEANUP_CRON="${XXL_JOB_PENDING_REGISTRATION_CLEANUP_CRON:-0 0/5 * * * ?}"

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
XXL_JOB_PENDING_REGISTRATION_CLEANUP_CRON_ESCAPED="$(sql_escape "${XXL_JOB_PENDING_REGISTRATION_CLEANUP_CRON}")"

echo "[xxl-job-seed] seeding xxl_job metadata..."

mysql --default-character-set=utf8mb4 -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
use \`xxl_job\`;
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
set @cleanup_cron := '${XXL_JOB_PENDING_REGISTRATION_CLEANUP_CRON_ESCAPED}';

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

update xxl_job_info
set job_desc = 'Pending Registration Cleanup',
    update_time = now(),
    author = @job_author,
    alarm_email = @alarm_email,
    schedule_type = 'CRON',
    schedule_conf = @cleanup_cron,
    misfire_strategy = 'DO_NOTHING',
    executor_route_strategy = 'FIRST',
    executor_handler = 'pendingRegistrationUserCleanup',
    executor_param = '',
    executor_block_strategy = 'SERIAL_EXECUTION',
    executor_timeout = 0,
    executor_fail_retry_count = 0,
    glue_type = 'BEAN',
    glue_source = '',
    glue_remark = 'seeded by deploy',
    glue_updatetime = now(),
    child_jobid = '',
    trigger_status = 1
where job_group = @job_group_id
  and executor_handler = 'pendingRegistrationUserCleanup';

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
  'Pending Registration Cleanup',
  now(),
  now(),
  @job_author,
  @alarm_email,
  'CRON',
  @cleanup_cron,
  'DO_NOTHING',
  'FIRST',
  'pendingRegistrationUserCleanup',
  '',
  'SERIAL_EXECUTION',
  0,
  0,
  'BEAN',
  '',
  'seeded by deploy',
  now(),
  '',
  1,
  0,
  0
from dual
where @job_group_id is not null
  and not exists (
    select 1
    from xxl_job_info
    where job_group = @job_group_id
      and executor_handler = 'pendingRegistrationUserCleanup'
  );

update xxl_job_info
set job_desc = 'Search Reindex',
    update_time = now(),
    author = @job_author,
    alarm_email = @alarm_email,
    schedule_type = 'NONE',
    schedule_conf = '',
    misfire_strategy = 'DO_NOTHING',
    executor_route_strategy = 'FIRST',
    executor_handler = 'searchReindex',
    executor_param = '',
    executor_block_strategy = 'SERIAL_EXECUTION',
    executor_timeout = 0,
    executor_fail_retry_count = 0,
    glue_type = 'BEAN',
    glue_source = '',
    glue_remark = 'seeded by deploy',
    glue_updatetime = now(),
    child_jobid = '',
    trigger_status = 0,
    trigger_last_time = 0,
    trigger_next_time = 0
where job_group = @job_group_id
  and executor_handler = 'searchReindex';

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
  'Search Reindex',
  now(),
  now(),
  @job_author,
  @alarm_email,
  'NONE',
  '',
  'DO_NOTHING',
  'FIRST',
  'searchReindex',
  '',
  'SERIAL_EXECUTION',
  0,
  0,
  'BEAN',
  '',
  'seeded by deploy',
  now(),
  '',
  0,
  0,
  0
from dual
where @job_group_id is not null
  and not exists (
    select 1
    from xxl_job_info
    where job_group = @job_group_id
      and executor_handler = 'searchReindex'
  );
SQL

echo "[xxl-job-seed] done."
