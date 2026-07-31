-- Idempotent compatibility migration for databases initialized by Nacos 2.3.2.
-- Nacos 3.1.2 requires the gray configuration table and three history columns.

CREATE TABLE IF NOT EXISTS `config_info_gray` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'id',
  `data_id` varchar(255) NOT NULL COMMENT 'data_id',
  `group_id` varchar(128) NOT NULL COMMENT 'group_id',
  `content` longtext NOT NULL COMMENT 'content',
  `md5` varchar(32) DEFAULT NULL COMMENT 'md5',
  `src_user` text COMMENT 'src_user',
  `src_ip` varchar(100) DEFAULT NULL COMMENT 'src_ip',
  `gmt_create` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'gmt_create',
  `gmt_modified` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'gmt_modified',
  `app_name` varchar(128) DEFAULT NULL COMMENT 'app_name',
  `tenant_id` varchar(128) DEFAULT '' COMMENT 'tenant_id',
  `gray_name` varchar(128) NOT NULL COMMENT 'gray_name',
  `gray_rule` text NOT NULL COMMENT 'gray_rule',
  `encrypted_data_key` varchar(256) NOT NULL DEFAULT '' COMMENT 'encrypted_data_key',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_configinfogray_datagrouptenantgray` (`data_id`,`group_id`,`tenant_id`,`gray_name`),
  KEY `idx_dataid_gmt_modified` (`data_id`,`gmt_modified`),
  KEY `idx_gmt_modified` (`gmt_modified`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8 COMMENT='config_info_gray';

SET @nacos_schema = DATABASE();

SET @nacos_sql = IF(
  EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = @nacos_schema
      AND table_name = 'his_config_info'
      AND column_name = 'publish_type'
  ),
  'SELECT 1',
  'ALTER TABLE `his_config_info` ADD COLUMN `publish_type` varchar(50) DEFAULT ''formal'' COMMENT ''publish type gray or formal'''
);
PREPARE nacos_schema_stmt FROM @nacos_sql;
EXECUTE nacos_schema_stmt;
DEALLOCATE PREPARE nacos_schema_stmt;

SET @nacos_sql = IF(
  EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = @nacos_schema
      AND table_name = 'his_config_info'
      AND column_name = 'gray_name'
  ),
  'SELECT 1',
  'ALTER TABLE `his_config_info` ADD COLUMN `gray_name` varchar(50) DEFAULT NULL COMMENT ''gray name'''
);
PREPARE nacos_schema_stmt FROM @nacos_sql;
EXECUTE nacos_schema_stmt;
DEALLOCATE PREPARE nacos_schema_stmt;

SET @nacos_sql = IF(
  EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = @nacos_schema
      AND table_name = 'his_config_info'
      AND column_name = 'ext_info'
  ),
  'SELECT 1',
  'ALTER TABLE `his_config_info` ADD COLUMN `ext_info` longtext DEFAULT NULL COMMENT ''ext info'''
);
PREPARE nacos_schema_stmt FROM @nacos_sql;
EXECUTE nacos_schema_stmt;
DEALLOCATE PREPARE nacos_schema_stmt;
