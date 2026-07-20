alter table moderation_action
  drop index idx_moderation_action_report,
  add unique key uk_moderation_action_report(report_id);
