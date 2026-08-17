package com.nowcoder.community.oss.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OssSchemaResourceTest {

    @Test
    void deploySchemaShouldCreateAllOssTables() throws Exception {
        String schema = Files.readString(Path.of(
                "..", "..", "deploy", "database", "business", "001_schema.sql"))
                .toLowerCase(java.util.Locale.ROOT);

        assertThat(schema).contains(
                "create table `oss_object`",
                "create table `oss_object_version`",
                "create table `oss_upload_session`",
                "create table `oss_access_grant`",
                "create table `oss_object_reference`",
                "create table `oss_usage_policy`"
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

    @Test
    void referenceMapperShouldInsertOnceInsteadOfBlindlyOverwritingAnExistingBinding() throws Exception {
        String mapper = Files.readString(
                Path.of("src", "main", "resources", "mapper", "oss_object_reference_mapper.xml"));

        assertThat(mapper).contains("<insert id=\"insert\"");
        assertThat(mapper).doesNotContain("<insert id=\"upsert\"");
        assertThat(mapper).doesNotContain("on duplicate key update");
        assertThat(mapper).contains("<select id=\"selectByIdForUpdate\"");
        assertThat(mapper).contains("for update");
    }
}
