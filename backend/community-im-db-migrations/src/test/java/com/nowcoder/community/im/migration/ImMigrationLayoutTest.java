package com.nowcoder.community.im.migration;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class ImMigrationLayoutTest {

    private static final String MIGRATION_RESOURCE =
            "db/migration/im-core/V001__im_core_baseline.sql";
    private static final String MANIFEST_RESOURCE =
            "db/migration/im-core/im-core-schema-manifest.tsv";
    private static final Set<String> IM_TABLES = Set.of(
            "outbox_event",
            "im_room",
            "im_room_member",
            "im_membership_version_counter",
            "im_membership_version_log",
            "im_room_message",
            "im_room_read_state",
            "im_conversation",
            "im_private_message",
            "im_conversation_read_state",
            "im_user_conversation_inbox",
            "im_user_room_inbox"
    );

    @Test
    void migrationModuleShouldPublishTheDedicatedRunnerCatalogAndVerifier() {
        assertThat(ImMigrationReflectionSupport.requireClass(
                ImMigrationReflectionSupport.APPLICATION_CLASS)).isNotNull();
        assertThat(ImMigrationReflectionSupport.requireClass(
                ImMigrationReflectionSupport.RUNNER_CLASS)).isNotNull();
        assertThat(ImMigrationReflectionSupport.requireClass(
                ImMigrationReflectionSupport.CATALOG_CLASS)).isNotNull();
        assertThat(ImMigrationReflectionSupport.requireClass(
                ImMigrationReflectionSupport.VERIFIER_CLASS)).isNotNull();
    }

    @Test
    void versionOneShouldBeDatabaseAgnosticAndOwnExactlyTheLegacyImCatalog() throws Exception {
        String sql = readClasspathResource(MIGRATION_RESOURCE).toLowerCase(Locale.ROOT);

        assertThat(sql)
                .doesNotContain("use im_core")
                .doesNotContain("create database")
                .doesNotContain("create table if not exists oss_")
                .doesNotContain("create table if not exists user")
                .contains("insert into im_membership_version_counter")
                .doesNotContain("insert ignore into im_membership_version_counter");

        Matcher tables = Pattern.compile(
                "(?i)create\\s+table\\s+if\\s+not\\s+exists\\s+`?([a-z0-9_]+)`?")
                .matcher(sql);
        Set<String> actualTables = new LinkedHashSet<>();
        while (tables.find()) {
            actualTables.add(tables.group(1));
        }
        assertThat(actualTables).containsExactlyInAnyOrderElementsOf(IM_TABLES);

        String manifest = readClasspathResource(MANIFEST_RESOURCE);
        Set<String> manifestTables = new LinkedHashSet<>();
        for (String line : manifest.split("\\R")) {
            if (!line.isBlank() && !line.startsWith("#")) {
                manifestTables.add(line.split("\\t", -1)[0]);
            }
        }
        assertThat(manifestTables).containsExactlyInAnyOrderElementsOf(IM_TABLES);
    }

    @Test
    void productionImCoreShouldNotLoadTheH2SchemaFixture() throws Exception {
        Path repositoryRoot = ImSchemaTestSupport.findRepositoryRoot();
        Path imCore = repositoryRoot.resolve("backend/community-im/im-core");

        assertThat(imCore.resolve("src/test/resources/schema.sql")).isRegularFile();
        assertThat(imCore.resolve("src/main/resources/schema.sql")).doesNotExist();
        assertThat(Files.readString(imCore.resolve("src/main/resources/application.yml")))
                .doesNotContain("schema.sql", "jdbc:h2:", "sql.init");

        var document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(imCore.resolve("pom.xml").toFile());
        NodeList dependencies = document.getElementsByTagName("dependency");
        String h2Scope = null;
        for (int index = 0; index < dependencies.getLength(); index++) {
            Element dependency = (Element) dependencies.item(index);
            if ("com.h2database".equals(childText(dependency, "groupId"))
                    && "h2".equals(childText(dependency, "artifactId"))) {
                h2Scope = childText(dependency, "scope");
            }
        }
        assertThat(h2Scope).isEqualTo("test");
    }

    private static String readClasspathResource(String resource) throws Exception {
        try (InputStream input = ImMigrationLayoutTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertThat(input).as(resource).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String childText(Element parent, String name) {
        NodeList children = parent.getElementsByTagName(name);
        return children.getLength() == 0 ? null : children.item(0).getTextContent().trim();
    }
}
