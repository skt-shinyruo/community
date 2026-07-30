-- Canonical current-state schema for disposable Community environments.
-- MySQL executes this file only when the primary data directory is empty.
-- Edit the final CREATE TABLE definitions in place and reset the MySQL volumes
-- after every schema change. This file is not safe to replay on a populated schema.

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

CREATE DATABASE IF NOT EXISTS `community` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `community`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_config` (
  `id` binary(16) NOT NULL,
  `name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `provider` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'openai',
  `base_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `api_key` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `model` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'gpt-4.1-mini',
  `enabled` tinyint(1) NOT NULL DEFAULT '0',
  `is_active` tinyint(1) NOT NULL DEFAULT '0',
  `timeout_ms` int NOT NULL DEFAULT '8000',
  `max_items_per_job` int NOT NULL DEFAULT '20',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `auth_refresh_token` (
  `token_hash` char(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` binary(16) NOT NULL,
  `family_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `security_version` bigint NOT NULL,
  `expires_at` timestamp NOT NULL,
  `state` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  `pending_expires_at` timestamp NULL DEFAULT NULL,
  `revoked_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`token_hash`),
  KEY `idx_refresh_family` (`family_id`,`expires_at`),
  KEY `idx_refresh_user` (`user_id`,`expires_at`),
  KEY `idx_refresh_state_pending` (`state`,`pending_expires_at`),
  CONSTRAINT `ck_auth_refresh_token_state` CHECK ((`state` in (_utf8mb4'ACTIVE',_utf8mb4'PENDING_ROTATION',_utf8mb4'CONSUMED',_utf8mb4'REVOKED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `auth_refresh_token_family_revocation` (
  `family_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `revoked_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`family_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `category` (
  `id` binary(16) NOT NULL,
  `name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `position` int DEFAULT '0',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_category_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

INSERT INTO `category` VALUES (0x00000000000070008000000000000101,'公告','官方公告/规则',0,'2026-07-28 04:49:55'),(0x00000000000070008000000000000102,'技术','技术讨论/问题求助',10,'2026-07-28 04:49:55'),(0x00000000000070008000000000000103,'兴趣','兴趣分享/作品展示',20,'2026-07-28 04:49:55');
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `comment` (
  `id` binary(16) NOT NULL,
  `post_id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `root_comment_id` binary(16) NOT NULL,
  `parent_comment_id` binary(16) DEFAULT NULL,
  `reply_to_user_id` binary(16) DEFAULT NULL,
  `content` text COLLATE utf8mb4_unicode_ci,
  `status` int DEFAULT '0',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NULL DEFAULT NULL,
  `edit_count` int DEFAULT '0',
  `deleted_by` binary(16) DEFAULT NULL,
  `deleted_reason` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `deleted_time` timestamp NULL DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_comment_post_root` (`post_id`,`parent_comment_id`,`create_time`,`id`),
  KEY `idx_comment_root_reply` (`root_comment_id`,`parent_comment_id`,`create_time`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `demo_batch` (
  `id` binary(16) NOT NULL,
  `batch_key` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `batch_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `requested_by` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `summary_json` mediumtext COLLATE utf8mb4_unicode_ci,
  `error_message` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `started_at` timestamp NULL DEFAULT NULL,
  `finished_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_demo_batch_key` (`batch_key`),
  KEY `idx_demo_batch_status_created` (`status`,`created_at`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `demo_batch_target` (
  `id` binary(16) NOT NULL,
  `batch_id` binary(16) NOT NULL,
  `entity_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_key` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_count` int NOT NULL DEFAULT '1',
  `payload_json` mediumtext COLLATE utf8mb4_unicode_ci,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_demo_batch_target` (`batch_id`,`entity_type`,`target_key`),
  KEY `idx_demo_batch_target_batch` (`batch_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `demo_entity_ref` (
  `id` binary(16) NOT NULL,
  `batch_id` binary(16) NOT NULL,
  `entity_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `entity_key` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_demo_entity_ref` (`batch_id`,`entity_type`,`entity_key`),
  KEY `idx_demo_entity_ref_batch` (`batch_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `demo_job` (
  `id` binary(16) NOT NULL,
  `batch_id` binary(16) NOT NULL,
  `job_key` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `job_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `summary_json` mediumtext COLLATE utf8mb4_unicode_ci,
  `error_message` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `started_at` timestamp NULL DEFAULT NULL,
  `finished_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_demo_job_batch_key` (`batch_id`,`job_key`),
  KEY `idx_demo_job_batch_status` (`batch_id`,`status`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `discuss_post` (
  `id` binary(16) NOT NULL,
  `user_id` binary(16) DEFAULT NULL,
  `category_id` binary(16) DEFAULT NULL,
  `title` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `type` int DEFAULT '0',
  `status` int DEFAULT '0',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NULL DEFAULT NULL,
  `edit_count` int DEFAULT '0',
  `deleted_by` binary(16) DEFAULT NULL,
  `deleted_reason` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `deleted_time` timestamp NULL DEFAULT NULL,
  `comment_count` int DEFAULT '0',
  `score` double DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_discuss_post_user_id` (`user_id`),
  KEY `idx_discuss_post_category_id` (`category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `drive_entry` (
  `entry_id` binary(16) NOT NULL,
  `space_id` binary(16) NOT NULL,
  `parent_id` binary(16) DEFAULT NULL,
  `parent_key` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
  `active_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `object_id` binary(16) DEFAULT NULL,
  `version_id` binary(16) DEFAULT NULL,
  `size_bytes` bigint NOT NULL DEFAULT '0',
  `mime_type` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `trashed_at` timestamp NULL DEFAULT NULL,
  `delete_after` timestamp NULL DEFAULT NULL,
  `trash_root_id` binary(16) DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`entry_id`),
  UNIQUE KEY `uk_drive_entry_active_name` (`space_id`,`parent_key`,`active_name`),
  KEY `idx_drive_entry_parent_status` (`space_id`,`parent_id`,`status`,`name`),
  KEY `idx_drive_entry_object` (`object_id`,`version_id`),
  KEY `idx_drive_entry_trash` (`space_id`,`status`,`trashed_at`),
  KEY `idx_drive_entry_search` (`space_id`,`status`,`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `drive_share` (
  `share_id` binary(16) NOT NULL,
  `entry_id` binary(16) NOT NULL,
  `share_token` varchar(96) COLLATE utf8mb4_unicode_ci NOT NULL,
  `password_hash` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `expires_at` timestamp NOT NULL,
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_by` binary(16) NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`share_id`),
  UNIQUE KEY `uk_drive_share_token` (`share_token`),
  KEY `idx_drive_share_entry_status` (`entry_id`,`status`),
  KEY `idx_drive_share_expiry` (`status`,`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `drive_share_access` (
  `access_id` binary(16) NOT NULL,
  `share_id` binary(16) NOT NULL,
  `visitor_fingerprint` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
  `success` tinyint(1) NOT NULL DEFAULT '0',
  `accessed_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`access_id`),
  KEY `idx_drive_share_access_share_time` (`share_id`,`accessed_at`),
  KEY `idx_drive_share_access_fingerprint_time` (`visitor_fingerprint`,`accessed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `drive_space` (
  `space_id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `quota_bytes` bigint NOT NULL DEFAULT '10737418240',
  `used_bytes` bigint NOT NULL DEFAULT '0',
  `reserved_bytes` bigint NOT NULL DEFAULT '0',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`space_id`),
  UNIQUE KEY `uk_drive_space_user` (`user_id`),
  KEY `idx_drive_space_updated` (`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `drive_upload` (
  `upload_id` binary(16) NOT NULL,
  `space_id` binary(16) NOT NULL,
  `parent_id` binary(16) DEFAULT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `size_bytes` bigint NOT NULL,
  `mime_type` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `object_id` binary(16) DEFAULT NULL,
  `version_id` binary(16) DEFAULT NULL,
  `oss_session_id` binary(16) DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_by` binary(16) NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `expires_at` timestamp NOT NULL,
  `completed_at` timestamp NULL DEFAULT NULL,
  `completed_entry_id` binary(16) DEFAULT NULL,
  `checksum_sha256` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
  PRIMARY KEY (`upload_id`),
  KEY `idx_drive_upload_space_status` (`space_id`,`status`,`expires_at`),
  KEY `idx_drive_upload_recovery` (`status`,`updated_at`,`upload_id`),
  KEY `idx_drive_upload_object` (`object_id`,`version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `http_idempotency` (
  `id` binary(16) NOT NULL,
  `operation` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` binary(16) NOT NULL,
  `idem_key` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `request_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `response_json` mediumtext COLLATE utf8mb4_unicode_ci,
  `processing_expires_at` timestamp NULL DEFAULT NULL,
  `success_expires_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_http_idem` (`operation`,`user_id`,`idem_key`),
  KEY `idx_http_idem_processing_expires` (`processing_expires_at`,`id`),
  KEY `idx_http_idem_success_expires` (`success_expires_at`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `market_address` (
  `address_id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `receiver_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `receiver_phone` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `province` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `city` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `district` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `detail_address` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `postal_code` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `is_default` tinyint(1) NOT NULL DEFAULT '0',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`address_id`),
  KEY `idx_market_address_user_status` (`user_id`,`status`,`is_default`,`address_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `market_delivery` (
  `delivery_id` binary(16) NOT NULL,
  `order_id` binary(16) NOT NULL,
  `seller_user_id` binary(16) NOT NULL,
  `delivery_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `delivery_content` varchar(8000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `delivered_at` timestamp NULL DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`delivery_id`),
  KEY `idx_market_delivery_order` (`order_id`,`delivery_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `market_dispute` (
  `dispute_id` binary(16) NOT NULL,
  `order_id` binary(16) NOT NULL,
  `goods_type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `buyer_user_id` binary(16) NOT NULL,
  `seller_user_id` binary(16) NOT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `reason` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `buyer_note` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `seller_note` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `resolution_type` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `resolved_by` binary(16) DEFAULT NULL,
  `resolved_at` timestamp NULL DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`dispute_id`),
  KEY `idx_market_dispute_order_status` (`order_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `market_inventory_unit` (
  `inventory_unit_id` binary(16) NOT NULL,
  `listing_id` binary(16) NOT NULL,
  `seller_user_id` binary(16) NOT NULL,
  `payload_type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `payload_content` varchar(4000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `reserved_order_id` binary(16) DEFAULT NULL,
  `delivered_at` timestamp NULL DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`inventory_unit_id`),
  KEY `idx_market_inventory_listing_status` (`listing_id`,`status`,`inventory_unit_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `market_listing` (
  `listing_id` binary(16) NOT NULL,
  `seller_user_id` binary(16) NOT NULL,
  `goods_type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `title` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `unit_price` bigint NOT NULL,
  `delivery_mode` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `stock_mode` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `stock_total` int NOT NULL,
  `stock_available` int NOT NULL,
  `min_purchase_quantity` int NOT NULL,
  `max_purchase_quantity` int NOT NULL,
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`listing_id`),
  KEY `idx_market_listing_seller_time` (`seller_user_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `market_order` (
  `order_id` binary(16) NOT NULL,
  `request_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `listing_id` binary(16) NOT NULL,
  `goods_type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `seller_user_id` binary(16) NOT NULL,
  `buyer_user_id` binary(16) NOT NULL,
  `quantity` int NOT NULL,
  `unit_price_snapshot` bigint NOT NULL,
  `total_amount` bigint NOT NULL,
  `delivery_mode_snapshot` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `listing_title_snapshot` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `escrow_txn_id` binary(16) DEFAULT NULL,
  `release_txn_id` binary(16) DEFAULT NULL,
  `refund_txn_id` binary(16) DEFAULT NULL,
  `auto_confirm_at` timestamp NULL DEFAULT NULL,
  `address_id_snapshot` binary(16) DEFAULT NULL,
  `receiver_name_snapshot` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `receiver_phone_snapshot` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `province_snapshot` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `city_snapshot` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `district_snapshot` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `detail_address_snapshot` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `postal_code_snapshot` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`order_id`),
  UNIQUE KEY `uk_market_order_buyer_request` (`buyer_user_id`,`request_id`),
  KEY `idx_market_order_buyer_time` (`buyer_user_id`,`create_time`),
  KEY `idx_market_order_seller_time` (`seller_user_id`,`create_time`),
  KEY `idx_market_order_listing_status` (`listing_id`,`status`),
  KEY `idx_market_order_auto_confirm` (`status`,`auto_confirm_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `market_shipment` (
  `shipment_id` binary(16) NOT NULL,
  `order_id` binary(16) NOT NULL,
  `seller_user_id` binary(16) NOT NULL,
  `carrier_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `tracking_no` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `shipping_remark` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `shipped_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`shipment_id`),
  UNIQUE KEY `uk_market_shipment_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `market_wallet_action` (
  `action_id` binary(16) NOT NULL,
  `order_id` binary(16) NOT NULL,
  `dispute_id` binary(16) DEFAULT NULL,
  `action_type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `request_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `wallet_biz_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `actor_user_id` binary(16) NOT NULL,
  `counterparty_user_id` binary(16) DEFAULT NULL,
  `amount` bigint NOT NULL,
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `result_type` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `wallet_txn_id` binary(16) DEFAULT NULL,
  `failure_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `last_error` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `retry_count` int NOT NULL DEFAULT '0',
  `next_retry_at` timestamp NULL DEFAULT NULL,
  `processing_lease_until` timestamp NULL DEFAULT NULL,
  `lease_token` binary(16) DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`action_id`),
  UNIQUE KEY `uk_market_wallet_action_request` (`request_id`),
  KEY `idx_market_wallet_action_status_next` (`status`,`next_retry_at`,`action_id`),
  KEY `idx_market_wallet_action_order_type` (`order_id`,`action_type`),
  KEY `idx_market_wallet_action_processing_lease` (`status`,`processing_lease_until`,`action_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `moderation_action` (
  `id` binary(16) NOT NULL,
  `report_id` binary(16) DEFAULT NULL,
  `actor_id` binary(16) NOT NULL,
  `action` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `reason` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `duration_seconds` int DEFAULT '0',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_moderation_action_report` (`report_id`),
  KEY `idx_moderation_action_actor` (`actor_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notice_projection_event_log` (
  `source_event_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`source_event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notice_record` (
  `id` binary(16) NOT NULL,
  `sender_user_id` binary(16) DEFAULT NULL,
  `recipient_user_id` binary(16) NOT NULL,
  `topic` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `content` mediumtext COLLATE utf8mb4_unicode_ci,
  `source_event_type` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `source_relation_key` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` int DEFAULT '0',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_notice_record_topic` (`topic`),
  KEY `idx_notice_record_recipient_status` (`recipient_user_id`,`status`),
  KEY `idx_notice_record_recipient_topic_time` (`recipient_user_id`,`topic`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ops_governance_audit` (
  `id` binary(16) NOT NULL,
  `action` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `actor_user_id` binary(16) NOT NULL,
  `target_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `scope` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reason` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `request_json` mediumtext COLLATE utf8mb4_unicode_ci,
  `result` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `summary_json` mediumtext COLLATE utf8mb4_unicode_ci,
  `trace_id` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ops_governance_action_created` (`action`,`created_at`,`id`),
  KEY `idx_ops_governance_actor_created` (`actor_user_id`,`created_at`,`id`),
  KEY `idx_ops_governance_target_created` (`target_type`,`target_id`,`created_at`),
  KEY `idx_ops_governance_result_created` (`result`,`created_at`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `outbox_event` (
  `id` binary(16) NOT NULL,
  `event_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `topic` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `event_key` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `payload` mediumtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `lease_token` binary(16) DEFAULT NULL,
  `processing_lease_until` timestamp NULL DEFAULT NULL,
  `retry_count` int NOT NULL DEFAULT '0',
  `next_retry_at` timestamp NULL DEFAULT NULL,
  `last_error` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `trace_id` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `traceparent` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_outbox_event_id` (`event_id`),
  KEY `idx_outbox_status_next` (`status`,`next_retry_at`,`id`),
  KEY `idx_outbox_status_updated` (`status`,`updated_at`,`id`),
  KEY `idx_outbox_status_created` (`status`,`created_at`,`id`),
  KEY `idx_outbox_processing_lease` (`status`,`processing_lease_until`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `post_bookmark` (
  `user_id` binary(16) NOT NULL,
  `post_id` binary(16) NOT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`,`post_id`),
  KEY `idx_post_bookmark_post` (`post_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `post_content_block` (
  `id` binary(16) NOT NULL,
  `post_id` binary(16) NOT NULL,
  `block_index` int NOT NULL,
  `block_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `text_content` text COLLATE utf8mb4_unicode_ci,
  `language` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `media_asset_id` binary(16) DEFAULT NULL,
  `caption` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `display_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `metadata_json` text COLLATE utf8mb4_unicode_ci,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_post_block_index` (`post_id`,`block_index`),
  KEY `idx_post_content_block_post` (`post_id`),
  KEY `idx_post_content_block_media` (`media_asset_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `post_counter_snapshot` (
  `post_id` binary(16) NOT NULL,
  `view_count` bigint NOT NULL DEFAULT '0',
  `like_count` bigint NOT NULL DEFAULT '0',
  `comment_count` bigint NOT NULL DEFAULT '0',
  `bookmark_count` bigint NOT NULL DEFAULT '0',
  `snapshot_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `post_media_asset` (
  `id` binary(16) NOT NULL,
  `owner_user_id` binary(16) NOT NULL,
  `post_id` binary(16) DEFAULT NULL,
  `oss_object_id` binary(16) NOT NULL,
  `oss_version_id` binary(16) DEFAULT NULL,
  `oss_reference_id` binary(16) DEFAULT NULL,
  `upload_session_id` binary(16) DEFAULT NULL,
  `file_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `content_type` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `content_length` bigint NOT NULL,
  `media_kind` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `lifecycle` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `upload_status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PREPARED',
  `upload_operation_version` bigint NOT NULL DEFAULT '0',
  `upload_updated_at` timestamp NULL DEFAULT NULL,
  `reference_status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'UNBOUND',
  `reference_operation_version` bigint NOT NULL DEFAULT '0',
  `reference_updated_at` timestamp NULL DEFAULT NULL,
  `video_state` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NONE',
  `public_url` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `failure_reason` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_post_media_asset_owner_lifecycle` (`owner_user_id`,`lifecycle`),
  KEY `idx_post_media_asset_post` (`post_id`),
  KEY `idx_post_media_asset_video_state` (`video_state`),
  KEY `idx_post_media_reference_pending` (`reference_status`,`reference_updated_at`),
  KEY `idx_post_media_upload_recovery` (`upload_status`,`upload_updated_at`,`id`),
  CONSTRAINT `ck_post_media_reference_status` CHECK ((`reference_status` in (_utf8mb4'UNBOUND',_utf8mb4'BIND_PENDING',_utf8mb4'BOUND',_utf8mb4'RELEASE_PENDING',_utf8mb4'RELEASED'))),
  CONSTRAINT `ck_post_media_upload_status` CHECK ((`upload_status` in (_utf8mb4'PREPARED',_utf8mb4'COMPLETING',_utf8mb4'OBJECT_COMPLETED',_utf8mb4'COMPLETED',_utf8mb4'FAILED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `post_score_snapshot` (
  `post_id` binary(16) NOT NULL,
  `score` double NOT NULL DEFAULT '0',
  `rank_version` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `snapshot_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `post_tag` (
  `post_id` binary(16) NOT NULL,
  `tag_id` binary(16) NOT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`post_id`,`tag_id`),
  KEY `idx_post_tag_post_id` (`post_id`),
  KEY `idx_post_tag_tag_id` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `recharge_order` (
  `order_id` binary(16) NOT NULL,
  `request_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` binary(16) NOT NULL,
  `amount` bigint NOT NULL,
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `channel` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `channel_order_id` varchar(96) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `remark` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`order_id`),
  UNIQUE KEY `uk_recharge_order_user_request` (`user_id`,`request_id`),
  KEY `idx_recharge_order_user_time` (`user_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `report` (
  `id` binary(16) NOT NULL,
  `reporter_id` binary(16) NOT NULL,
  `target_type` int NOT NULL,
  `target_id` binary(16) NOT NULL,
  `reason` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `detail` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `status` int DEFAULT '0',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_report_dedupe` (`reporter_id`,`target_type`,`target_id`),
  KEY `idx_report_status` (`status`,`create_time`),
  KEY `idx_report_target` (`target_type`,`target_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `social_block` (
  `user_id` binary(16) NOT NULL,
  `target_user_id` binary(16) NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`user_id`,`target_user_id`),
  KEY `idx_block_user_created` (`user_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `social_block_version_counter` (
  `id` int NOT NULL,
  `current_version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

INSERT INTO `social_block_version_counter` VALUES (1,0);
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `social_block_version_log` (
  `version` bigint NOT NULL,
  `user_id` binary(16) NOT NULL,
  `target_user_id` binary(16) NOT NULL,
  `active` tinyint(1) NOT NULL,
  `occurred_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`version`),
  KEY `idx_social_block_version_pair` (`user_id`,`target_user_id`,`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `social_follow` (
  `user_id` binary(16) NOT NULL,
  `entity_type` int NOT NULL,
  `entity_id` binary(16) NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`,`entity_type`,`entity_id`),
  KEY `idx_follow_followee` (`user_id`,`entity_type`,`created_at`,`entity_id`),
  KEY `idx_follow_follower` (`entity_type`,`entity_id`,`created_at`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `social_like` (
  `user_id` binary(16) NOT NULL,
  `entity_type` int NOT NULL,
  `entity_id` binary(16) NOT NULL,
  `entity_user_id` binary(16) DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `relation_instance_id` binary(16) NOT NULL,
  PRIMARY KEY (`user_id`,`entity_type`,`entity_id`),
  UNIQUE KEY `uk_social_like_relation_instance` (`relation_instance_id`),
  KEY `idx_like_entity` (`entity_type`,`entity_id`),
  KEY `idx_like_entity_user` (`entity_type`,`entity_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `social_like_target_state` (
  `entity_type` int NOT NULL,
  `entity_id` binary(16) NOT NULL,
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  `source_event_id` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `source_version` bigint NOT NULL DEFAULT '0',
  `deleted_at` timestamp NULL DEFAULT NULL,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`entity_type`,`entity_id`),
  KEY `idx_social_like_target_state_status_updated` (`status`,`updated_at`),
  CONSTRAINT `ck_social_like_target_state_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'DELETED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `social_user_like_count` (
  `user_id` binary(16) NOT NULL,
  `like_count` bigint NOT NULL DEFAULT '0',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tag` (
  `id` binary(16) NOT NULL,
  `name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tag_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `task_template` (
  `task_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `task_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `period_type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `trigger_event_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_value` int NOT NULL,
  `reward_growth_delta` int NOT NULL DEFAULT '0',
  `reward_balance_delta` int NOT NULL DEFAULT '0',
  `claim_required` tinyint(1) NOT NULL DEFAULT '0',
  `display_order` int NOT NULL DEFAULT '0',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`task_code`),
  KEY `idx_task_template_trigger` (`trigger_event_type`,`status`,`display_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

INSERT INTO `task_template` VALUES ('DAILY_CHECK_IN','CHECK_IN','DAILY','DailyCheckIn',1,2,1,0,10,'ACTIVE','2026-07-28 04:49:55','2026-07-28 04:49:55'),('DAILY_POST','CONTENT','DAILY','PostPublished',1,3,1,0,20,'ACTIVE','2026-07-28 04:49:55','2026-07-28 04:49:55'),('LIFETIME_RECEIVE_LIKE','SOCIAL','LIFETIME','LikeCreated',3,6,2,0,40,'ACTIVE','2026-07-28 04:49:55','2026-07-28 04:49:55'),('WEEKLY_COMMENTER','CONTENT','WEEKLY','CommentCreated',2,4,1,0,30,'ACTIVE','2026-07-28 04:49:55','2026-07-28 04:49:55');
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `transfer_order` (
  `order_id` binary(16) NOT NULL,
  `request_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `from_user_id` binary(16) NOT NULL,
  `to_user_id` binary(16) NOT NULL,
  `amount` bigint NOT NULL,
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `remark` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`order_id`),
  UNIQUE KEY `uk_transfer_order_from_request` (`from_user_id`,`request_id`),
  KEY `idx_transfer_order_from_user_time` (`from_user_id`,`create_time`),
  KEY `idx_transfer_order_to_user_time` (`to_user_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user` (
  `id` binary(16) NOT NULL,
  `username` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `password` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `salt` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `email` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `type` int NOT NULL DEFAULT '0',
  `status` int DEFAULT '0',
  `header_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `mute_until` timestamp NULL DEFAULT NULL,
  `ban_until` timestamp NULL DEFAULT NULL,
  `policy_version` bigint NOT NULL DEFAULT '0',
  `security_version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_username` (`username`),
  UNIQUE KEY `uk_user_email` (`email`),
  CONSTRAINT `ck_user_type` CHECK ((`type` in (0,1,2)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_consumed_event` (
  `id` binary(16) NOT NULL,
  `event_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `consumed_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_consumed_event_id` (`event_id`),
  KEY `idx_user_consumed_event_at` (`consumed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_level_rule_config` (
  `id` binary(16) NOT NULL,
  `config_key` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `window_days` int NOT NULL,
  `lv2_sign_in_days` int NOT NULL,
  `lv3_sign_in_days` int NOT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT '1',
  `updated_by` binary(16) DEFAULT NULL,
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_level_rule_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_policy_version_counter` (
  `id` int NOT NULL,
  `current_version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

INSERT INTO `user_policy_version_counter` VALUES (1,0);
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_security_version_counter` (
  `id` int NOT NULL,
  `current_version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

INSERT INTO `user_security_version_counter` VALUES (1,7312237344329728);
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_subscription_category` (
  `user_id` binary(16) NOT NULL,
  `category_id` binary(16) NOT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`,`category_id`),
  KEY `idx_user_sub_category_user` (`user_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_task_event_log` (
  `id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `task_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `period_key` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_event_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_task_event` (`user_id`,`task_code`,`period_key`,`source_event_id`),
  KEY `idx_user_task_event_lookup` (`user_id`,`task_code`,`period_key`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_task_progress` (
  `id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `task_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `period_key` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `current_value` int NOT NULL,
  `target_value` int NOT NULL,
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `reached_at` timestamp NULL DEFAULT NULL,
  `claimed_at` timestamp NULL DEFAULT NULL,
  `reward_grant_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `last_source_event_id` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_task_period` (`user_id`,`task_code`,`period_key`),
  KEY `idx_user_task_lookup` (`user_id`,`status`,`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wallet_account` (
  `account_id` binary(16) NOT NULL,
  `owner_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `owner_id` binary(16) NOT NULL,
  `account_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `balance` bigint NOT NULL DEFAULT '0',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`account_id`),
  UNIQUE KEY `uk_wallet_account_owner` (`owner_type`,`owner_id`,`account_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wallet_admin_action` (
  `action_id` binary(16) NOT NULL,
  `request_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `actor_user_id` binary(16) NOT NULL,
  `target_account_id` binary(16) NOT NULL,
  `action_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `amount` bigint NOT NULL,
  `remark` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`action_id`),
  UNIQUE KEY `uk_wallet_admin_action_request` (`request_id`),
  KEY `idx_wallet_admin_action_target_time` (`target_account_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wallet_entry` (
  `entry_id` binary(16) NOT NULL,
  `txn_id` binary(16) NOT NULL,
  `account_id` binary(16) NOT NULL,
  `direction` varchar(8) COLLATE utf8mb4_unicode_ci NOT NULL,
  `amount` bigint NOT NULL,
  `balance_after` bigint NOT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`entry_id`),
  KEY `idx_wallet_entry_txn` (`txn_id`),
  KEY `idx_wallet_entry_account_time` (`account_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wallet_txn` (
  `txn_id` binary(16) NOT NULL,
  `request_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `txn_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `biz_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `biz_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `amount` bigint NOT NULL,
  `remark` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`txn_id`),
  UNIQUE KEY `uk_wallet_txn_request` (`request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `withdraw_order` (
  `order_id` binary(16) NOT NULL,
  `request_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` binary(16) NOT NULL,
  `amount` bigint NOT NULL,
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `payee_account` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `failure_reason` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`order_id`),
  UNIQUE KEY `uk_withdraw_order_user_request` (`user_id`,`request_id`),
  KEY `idx_withdraw_order_user_time` (`user_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;


CREATE DATABASE IF NOT EXISTS `community_oss` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `community_oss`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `oss_access_grant` (
  `grant_id` binary(16) NOT NULL,
  `object_id` binary(16) NOT NULL,
  `version_id` binary(16) DEFAULT NULL,
  `principal_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `principal_value` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `permission` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `expires_at` timestamp NULL DEFAULT NULL,
  `created_by` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `revoked_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`grant_id`),
  KEY `idx_oss_access_object` (`object_id`,`version_id`),
  KEY `idx_oss_access_principal` (`principal_type`,`principal_value`,`permission`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `oss_object` (
  `object_id` binary(16) NOT NULL,
  `usage` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `owner_service` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `owner_domain` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `owner_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `owner_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `visibility` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `current_version_id` binary(16) DEFAULT NULL,
  `latest_file_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
  `latest_content_type` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'application/octet-stream',
  `latest_content_length` bigint NOT NULL DEFAULT '0',
  `latest_checksum_sha256` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
  `retention_until` timestamp NULL DEFAULT NULL,
  `delete_after` timestamp NULL DEFAULT NULL,
  `legal_hold_until` timestamp NULL DEFAULT NULL,
  `created_by` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`object_id`),
  KEY `idx_oss_object_owner` (`owner_service`,`owner_domain`,`owner_type`,`owner_id`),
  KEY `idx_oss_object_status` (`status`,`updated_at`),
  KEY `idx_oss_object_current_version` (`current_version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `oss_object_reference` (
  `reference_id` binary(16) NOT NULL,
  `object_id` binary(16) NOT NULL,
  `version_id` binary(16) DEFAULT NULL,
  `subject_service` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `subject_domain` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `subject_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `subject_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `reference_role` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `retain_until` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `released_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`reference_id`),
  KEY `idx_oss_reference_object` (`object_id`,`version_id`,`status`),
  KEY `idx_oss_reference_subject` (`subject_service`,`subject_domain`,`subject_type`,`subject_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `oss_object_version` (
  `version_id` binary(16) NOT NULL,
  `object_id` binary(16) NOT NULL,
  `version_no` int NOT NULL,
  `storage_backend` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `storage_bucket` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `storage_key` varchar(1024) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `file_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `content_type` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'application/octet-stream',
  `content_length` bigint NOT NULL DEFAULT '0',
  `checksum_sha256` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
  `etag` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
  `cache_control` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
  `content_disposition` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
  `source_object_id` binary(16) DEFAULT NULL,
  `variant_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `activated_at` timestamp NULL DEFAULT NULL,
  `expired_at` timestamp NULL DEFAULT NULL,
  `purged_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`version_id`),
  UNIQUE KEY `uk_oss_object_version_no` (`object_id`,`version_no`),
  KEY `idx_oss_object_version_object_status` (`object_id`,`status`),
  KEY `idx_oss_object_version_source` (`source_object_id`,`variant_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `oss_upload_session` (
  `session_id` binary(16) NOT NULL,
  `request_id` binary(16) NOT NULL,
  `object_id` binary(16) NOT NULL,
  `version_id` binary(16) NOT NULL,
  `upload_mode` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `owner_service` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `owner_domain` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `owner_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `owner_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `expected_file_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `expected_content_type` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'application/octet-stream',
  `expected_content_length` bigint NOT NULL DEFAULT '0',
  `expected_checksum_sha256` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `claim_version` bigint NOT NULL DEFAULT '0',
  `expires_at` timestamp NOT NULL,
  `created_by` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL,
  `completed_at` timestamp NULL DEFAULT NULL,
  `last_error` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
  PRIMARY KEY (`session_id`),
  UNIQUE KEY `uk_oss_upload_request` (`request_id`),
  KEY `idx_oss_upload_object` (`object_id`,`version_id`),
  KEY `idx_oss_upload_status_expiry` (`status`,`expires_at`),
  KEY `idx_oss_upload_recovery` (`status`,`updated_at`,`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `oss_usage_policy` (
  `usage` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `default_visibility` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `max_bytes` bigint NOT NULL,
  `allowed_mime_types` varchar(1024) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
  `requires_checksum` tinyint(1) NOT NULL DEFAULT '0',
  `requires_scan` tinyint(1) NOT NULL DEFAULT '0',
  `versioning_enabled` tinyint(1) NOT NULL DEFAULT '1',
  `download_ttl_seconds` bigint NOT NULL DEFAULT '300',
  `upload_ttl_seconds` bigint NOT NULL DEFAULT '900',
  `public_cache_control` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
  `private_cache_control` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'no-store',
  `retention_days` int NOT NULL DEFAULT '0',
  `delete_grace_days` int NOT NULL DEFAULT '7',
  PRIMARY KEY (`usage`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

INSERT INTO `oss_usage_policy` VALUES ('DRIVE_FILE','PRIVATE',10737418240,'',0,0,1,300,900,'','no-store',0,7);

CREATE DATABASE IF NOT EXISTS `im_core` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `im_core`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `im_conversation` (
  `conversation_id` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_a` binary(16) NOT NULL,
  `user_b` binary(16) NOT NULL,
  `last_seq` bigint NOT NULL DEFAULT '0',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`conversation_id`),
  KEY `idx_im_conversation_users` (`user_a`,`user_b`,`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `im_conversation_read_state` (
  `conversation_id` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` binary(16) NOT NULL,
  `last_read_seq` bigint NOT NULL DEFAULT '0',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`conversation_id`,`user_id`),
  KEY `idx_im_conversation_read_state_user` (`user_id`,`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `im_membership_version_counter` (
  `id` int NOT NULL,
  `current_version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

INSERT INTO `im_membership_version_counter` VALUES (1,0);
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `im_membership_version_log` (
  `version` bigint NOT NULL,
  `room_id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `active` tinyint(1) NOT NULL,
  `occurred_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`version`),
  KEY `idx_im_membership_version_pair` (`room_id`,`user_id`,`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `im_private_message` (
  `conversation_id` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `seq` bigint NOT NULL,
  `message_id` binary(16) NOT NULL,
  `from_user_id` binary(16) NOT NULL,
  `to_user_id` binary(16) NOT NULL,
  `content` mediumtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `client_msg_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`conversation_id`,`seq`),
  UNIQUE KEY `uk_im_private_message_idempotency` (`conversation_id`,`from_user_id`,`client_msg_id`),
  UNIQUE KEY `uk_im_private_message_id` (`message_id`),
  KEY `idx_im_private_message_to` (`to_user_id`,`conversation_id`,`seq`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `im_room` (
  `room_id` binary(16) NOT NULL,
  `name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `last_seq` bigint NOT NULL DEFAULT '0',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`room_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `im_room_member` (
  `room_id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `role` tinyint NOT NULL DEFAULT '0',
  `joined_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`room_id`,`user_id`),
  KEY `idx_im_room_member_user` (`user_id`,`room_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `im_room_message` (
  `room_id` binary(16) NOT NULL,
  `seq` bigint NOT NULL,
  `message_id` binary(16) NOT NULL,
  `from_user_id` binary(16) NOT NULL,
  `content` mediumtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `client_msg_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`room_id`,`seq`),
  UNIQUE KEY `uk_im_room_message_idempotency` (`room_id`,`from_user_id`,`client_msg_id`),
  UNIQUE KEY `uk_im_room_message_id` (`message_id`),
  KEY `idx_im_room_message_created_at` (`room_id`,`created_at`,`seq`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `im_room_read_state` (
  `room_id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `last_read_seq` bigint NOT NULL DEFAULT '0',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`room_id`,`user_id`),
  KEY `idx_im_room_read_state_user` (`user_id`,`room_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `im_user_conversation_inbox` (
  `user_id` binary(16) NOT NULL,
  `conversation_id` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `peer_user_id` binary(16) NOT NULL,
  `last_seq` bigint NOT NULL DEFAULT '0',
  `last_message_id` binary(16) DEFAULT NULL,
  `last_from_user_id` binary(16) DEFAULT NULL,
  `last_to_user_id` binary(16) DEFAULT NULL,
  `last_content` mediumtext COLLATE utf8mb4_unicode_ci,
  `last_message_created_at` timestamp NULL DEFAULT NULL,
  `last_read_seq` bigint NOT NULL DEFAULT '0',
  `unread_count` bigint NOT NULL DEFAULT '0',
  `sort_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`,`conversation_id`),
  KEY `idx_im_user_conversation_inbox_user_sort` (`user_id`,`sort_at`,`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `im_user_room_inbox` (
  `user_id` binary(16) NOT NULL,
  `room_id` binary(16) NOT NULL,
  `last_seq` bigint NOT NULL DEFAULT '0',
  `last_message_id` binary(16) DEFAULT NULL,
  `last_from_user_id` binary(16) DEFAULT NULL,
  `last_content` mediumtext COLLATE utf8mb4_unicode_ci,
  `last_message_created_at` timestamp NULL DEFAULT NULL,
  `last_read_seq` bigint NOT NULL DEFAULT '0',
  `unread_count` bigint NOT NULL DEFAULT '0',
  `sort_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`,`room_id`),
  KEY `idx_im_user_room_inbox_user_sort` (`user_id`,`sort_at`,`room_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `outbox_event` (
  `id` binary(16) NOT NULL,
  `event_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `topic` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `event_key` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `payload` mediumtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `lease_token` binary(16) DEFAULT NULL,
  `processing_lease_until` timestamp NULL DEFAULT NULL,
  `retry_count` int NOT NULL DEFAULT '0',
  `next_retry_at` timestamp NULL DEFAULT NULL,
  `last_error` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `trace_id` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `traceparent` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_outbox_event_id` (`event_id`),
  KEY `idx_outbox_status_next` (`status`,`next_retry_at`,`id`),
  KEY `idx_outbox_status_updated` (`status`,`updated_at`,`id`),
  KEY `idx_outbox_status_created` (`status`,`created_at`,`id`),
  KEY `idx_outbox_processing_lease` (`status`,`processing_lease_until`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
