package com.nowcoder.community.oss.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OssSchemaResourceTest {

    @Test
    void deploySchemaShouldCreateAllOssTables() throws Exception {
        String schema = Files.readString(Path.of("..", "..", "deploy", "mysql", "community_oss", "010_schema.sql"));

        assertThat(schema).contains(
                "create table if not exists oss_object",
                "create table if not exists oss_object_version",
                "create table if not exists oss_upload_session",
                "create table if not exists oss_access_grant",
                "create table if not exists oss_object_reference",
                "create table if not exists oss_usage_policy"
        );
    }

    @Test
    void myBatisMapperResourcesShouldExistForOssRepositories() {
        assertThat(Path.of("src", "main", "resources", "mapper", "oss_object_mapper.xml")).exists();
        assertThat(Path.of("src", "main", "resources", "mapper", "oss_object_version_mapper.xml")).exists();
        assertThat(Path.of("src", "main", "resources", "mapper", "oss_upload_session_mapper.xml")).exists();
        assertThat(Path.of("src", "main", "resources", "mapper", "oss_usage_policy_mapper.xml")).exists();
        assertThat(Path.of("src", "main", "resources", "mapper", "oss_access_grant_mapper.xml")).exists();
        assertThat(Path.of("src", "main", "resources", "mapper", "oss_object_reference_mapper.xml")).exists();
    }
}
