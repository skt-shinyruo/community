package com.nowcoder.community.im.migration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ImSchemaTestSupport {

    static final Set<String> IM_TABLES = Set.of(
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

    private static final String MYSQL_COLUMNS = """
            select table_name, ordinal_position, column_name, column_type, is_nullable,
                   column_default, extra, character_set_name, collation_name, generation_expression
              from information_schema.columns
             where table_schema = ?
             order by table_name, ordinal_position
            """;
    private static final String MYSQL_INDEXES = """
            select table_name, index_name, non_unique, seq_in_index, column_name, collation,
                   sub_part, packed, nullable, index_type, comment, index_comment, is_visible, expression
              from information_schema.statistics
             where table_schema = ?
             order by table_name, index_name, seq_in_index
            """;
    private static final String MYSQL_CONSTRAINTS = """
            select table_name, constraint_name, constraint_type, enforced
              from information_schema.table_constraints
             where constraint_schema = ?
             order by table_name, constraint_name
            """;
    private static final String MYSQL_KEY_COLUMNS = """
            select table_name, constraint_name, ordinal_position, position_in_unique_constraint,
                   column_name, referenced_table_name, referenced_column_name
              from information_schema.key_column_usage
             where constraint_schema = ?
             order by table_name, constraint_name, ordinal_position
            """;

    private ImSchemaTestSupport() {
    }

    static Path findRepositoryRoot() {
        Path candidate = Path.of("").toAbsolutePath().normalize();
        while (candidate != null) {
            if (Files.isRegularFile(candidate.resolve("deploy/mysql/community/070_schema_im_core.sql"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("cannot locate deploy/mysql/community/070_schema_im_core.sql");
    }

    static String legacySchemaSql() throws IOException {
        String sql = Files.readString(findRepositoryRoot().resolve(
                "deploy/mysql/community/070_schema_im_core.sql"));
        return sql.replaceAll("(?im)^\\s*use\\s+`?im_core`?\\s*;\\s*", "");
    }

    static Path h2SchemaFixture() {
        return findRepositoryRoot().resolve(
                "backend/community-im/im-core/src/test/resources/schema.sql");
    }

    static void executeStatements(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String candidate : sql.split(";")) {
                if (!candidate.isBlank()) {
                    statement.execute(candidate);
                }
            }
        }
    }

    static ExactCatalog captureMysqlExact(
            String jdbcUrl,
            String username,
            String password,
            Set<String> excludedTables
    ) throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            String schema = connection.getCatalog();
            SortedMap<String, List<String>> columns = captureRows(
                    connection, schema, MYSQL_COLUMNS, 2, 10, excludedTables);
            SortedMap<String, List<String>> indexes = captureRows(
                    connection, schema, MYSQL_INDEXES, 2, 14, excludedTables);
            SortedMap<String, List<String>> constraints = captureRows(
                    connection, schema, MYSQL_CONSTRAINTS, 2, 4, excludedTables);
            mergeRows(constraints, captureRows(
                    connection, schema, MYSQL_KEY_COLUMNS, 2, 7, excludedTables));
            return new ExactCatalog(columns, indexes, constraints);
        }
    }

    static PortableCatalog captureMysqlPortable(
            String jdbcUrl,
            String username,
            String password,
            Set<String> excludedTables
    ) throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            String schema = connection.getCatalog();
            SortedMap<String, List<PortableColumn>> columns = new TreeMap<>();
            try (PreparedStatement statement = connection.prepareStatement(MYSQL_COLUMNS)) {
                statement.setString(1, schema);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        String table = rows.getString("table_name").toLowerCase(Locale.ROOT);
                        if (excludedTables.contains(table)) {
                            continue;
                        }
                        columns.computeIfAbsent(table, ignored -> new ArrayList<>()).add(new PortableColumn(
                                rows.getInt("ordinal_position"),
                                rows.getString("column_name").toLowerCase(Locale.ROOT),
                                mysqlPortableType(rows.getString("column_type")),
                                "YES".equals(rows.getString("is_nullable")),
                                normalizeDefault(rows.getString("column_default"))
                        ));
                    }
                }
            }
            return new PortableCatalog(
                    columns,
                    captureMysqlPrimaryKeys(connection, schema, excludedTables),
                    captureMysqlIndexes(connection, schema, excludedTables)
            );
        }
    }

    static PortableCatalog captureH2Portable(Connection connection) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        SortedMap<String, List<PortableColumn>> columns = new TreeMap<>();
        Map<String, String> schemas = new TreeMap<>();
        try (ResultSet tables = metadata.getTables(null, null, "%", new String[]{"TABLE"})) {
            while (tables.next()) {
                String schema = tables.getString("TABLE_SCHEM");
                if (schema == null || !"PUBLIC".equalsIgnoreCase(schema)) {
                    continue;
                }
                String table = tables.getString("TABLE_NAME").toLowerCase(Locale.ROOT);
                schemas.put(table, schema);
                columns.put(table, new ArrayList<>());
            }
        }
        for (Map.Entry<String, String> table : schemas.entrySet()) {
            try (ResultSet rows = metadata.getColumns(
                    null, table.getValue(), table.getKey(), "%")) {
                while (rows.next()) {
                    columns.get(table.getKey()).add(new PortableColumn(
                            rows.getInt("ORDINAL_POSITION"),
                            rows.getString("COLUMN_NAME").toLowerCase(Locale.ROOT),
                            h2PortableType(rows.getString("TYPE_NAME"), rows.getLong("COLUMN_SIZE")),
                            rows.getInt("NULLABLE") == DatabaseMetaData.columnNullable,
                            normalizeDefault(rows.getString("COLUMN_DEF"))
                    ));
                }
            }
        }

        SortedMap<String, List<String>> primaryKeys = new TreeMap<>();
        SortedMap<String, List<String>> indexes = new TreeMap<>();
        for (Map.Entry<String, String> table : schemas.entrySet()) {
            List<String> primaryKey = readJdbcPrimaryKey(
                    metadata, table.getValue(), table.getKey());
            primaryKeys.put(table.getKey(), primaryKey);
            indexes.put(table.getKey(), readJdbcIndexes(
                    metadata, table.getValue(), table.getKey(), primaryKey));
        }
        return new PortableCatalog(columns, primaryKeys, indexes);
    }

    static Set<String> mysqlOnUpdateColumns(
            String jdbcUrl,
            String username,
            String password
    ) throws SQLException {
        String sql = """
                select table_name, column_name
                  from information_schema.columns
                 where table_schema = ?
                   and lower(extra) like '%on update%'
                 order by table_name, ordinal_position
                """;
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, connection.getCatalog());
            try (ResultSet rows = statement.executeQuery()) {
                Set<String> columns = new LinkedHashSet<>();
                while (rows.next()) {
                    columns.add(rows.getString(1).toLowerCase(Locale.ROOT) + "."
                            + rows.getString(2).toLowerCase(Locale.ROOT));
                }
                return columns;
            }
        }
    }

    static Set<String> declaredOnUpdateColumns(String sql) {
        Set<String> columns = new LinkedHashSet<>();
        Pattern tablePattern = Pattern.compile(
                "(?i)create\\s+table\\s+if\\s+not\\s+exists\\s+`?([a-z0-9_]+)`?");
        for (String statement : sql.split(";")) {
            Matcher tableMatcher = tablePattern.matcher(statement);
            if (!tableMatcher.find()) {
                continue;
            }
            int bodyStart = statement.indexOf('(', tableMatcher.end());
            int bodyEnd = statement.lastIndexOf(')');
            if (bodyStart < 0 || bodyEnd <= bodyStart) {
                continue;
            }
            String table = tableMatcher.group(1).toLowerCase(Locale.ROOT);
            for (String definition : splitTopLevel(statement.substring(bodyStart + 1, bodyEnd))) {
                String normalized = definition.strip().toLowerCase(Locale.ROOT);
                if (!normalized.contains("on update current_timestamp")) {
                    continue;
                }
                Matcher column = Pattern.compile("^`?([a-z0-9_]+)`?\\s+").matcher(normalized);
                if (column.find()) {
                    columns.add(table + "." + column.group(1));
                }
            }
        }
        return columns;
    }

    private static SortedMap<String, List<String>> captureRows(
            Connection connection,
            String schema,
            String sql,
            int firstValueColumn,
            int lastValueColumn,
            Set<String> excludedTables
    ) throws SQLException {
        SortedMap<String, List<String>> result = new TreeMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, schema);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    String table = rows.getString(1).toLowerCase(Locale.ROOT);
                    if (excludedTables.contains(table)) {
                        continue;
                    }
                    StringBuilder encoded = new StringBuilder();
                    for (int column = firstValueColumn; column <= lastValueColumn; column++) {
                        String value = rows.getString(column);
                        encoded.append('|');
                        encoded.append(rows.wasNull() ? "-1:" : value.length() + ":" + value);
                    }
                    result.computeIfAbsent(table, ignored -> new ArrayList<>()).add(encoded.toString());
                }
            }
        }
        return result;
    }

    private static void mergeRows(
            SortedMap<String, List<String>> target,
            SortedMap<String, List<String>> source
    ) {
        source.forEach((table, rows) -> target
                .computeIfAbsent(table, ignored -> new ArrayList<>())
                .addAll(rows));
    }

    private static SortedMap<String, List<String>> captureMysqlPrimaryKeys(
            Connection connection,
            String schema,
            Set<String> excludedTables
    ) throws SQLException {
        String sql = """
                select table_name, seq_in_index, column_name
                  from information_schema.statistics
                 where table_schema = ? and index_name = 'PRIMARY'
                 order by table_name, seq_in_index
                """;
        SortedMap<String, List<String>> primaryKeys = new TreeMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, schema);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    String table = rows.getString(1).toLowerCase(Locale.ROOT);
                    if (!excludedTables.contains(table)) {
                        primaryKeys.computeIfAbsent(table, ignored -> new ArrayList<>())
                                .add(rows.getString(3).toLowerCase(Locale.ROOT));
                    }
                }
            }
        }
        return primaryKeys;
    }

    private static SortedMap<String, List<String>> captureMysqlIndexes(
            Connection connection,
            String schema,
            Set<String> excludedTables
    ) throws SQLException {
        String sql = """
                select table_name, index_name, non_unique, seq_in_index, column_name
                  from information_schema.statistics
                 where table_schema = ? and index_name <> 'PRIMARY'
                 order by table_name, index_name, seq_in_index
                """;
        Map<String, Map<String, IndexBuilder>> builders = new TreeMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, schema);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    String table = rows.getString(1).toLowerCase(Locale.ROOT);
                    if (excludedTables.contains(table)) {
                        continue;
                    }
                    boolean unique = rows.getInt(3) == 0;
                    builders.computeIfAbsent(table, ignored -> new TreeMap<>())
                            .computeIfAbsent(rows.getString(2), ignored ->
                                    new IndexBuilder(rowsString(rows, 2), unique))
                            .columns.add(rows.getString(5).toLowerCase(Locale.ROOT));
                }
            }
        }
        SortedMap<String, List<String>> indexes = new TreeMap<>();
        builders.forEach((table, tableIndexes) -> indexes.put(
                table,
                tableIndexes.values().stream().map(IndexBuilder::encode).sorted().toList()
        ));
        return indexes;
    }

    private static List<String> readJdbcPrimaryKey(
            DatabaseMetaData metadata,
            String schema,
            String table
    ) throws SQLException {
        SortedMap<Integer, String> columns = new TreeMap<>();
        try (ResultSet rows = metadata.getPrimaryKeys(null, schema, table)) {
            while (rows.next()) {
                columns.put(rows.getInt("KEY_SEQ"),
                        rows.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
            }
        }
        return List.copyOf(columns.values());
    }

    private static List<String> readJdbcIndexes(
            DatabaseMetaData metadata,
            String schema,
            String table,
            List<String> primaryKey
    ) throws SQLException {
        Map<String, IndexBuilder> builders = new LinkedHashMap<>();
        try (ResultSet rows = metadata.getIndexInfo(null, schema, table, false, false)) {
            while (rows.next()) {
                String indexName = rows.getString("INDEX_NAME");
                String columnName = rows.getString("COLUMN_NAME");
                if (indexName == null || columnName == null) {
                    continue;
                }
                builders.computeIfAbsent(indexName, ignored ->
                                new IndexBuilder(indexName, !rowsBoolean(rows, "NON_UNIQUE")))
                        .columns.add(columnName.toLowerCase(Locale.ROOT));
            }
        }
        TreeSet<String> indexes = new TreeSet<>();
        for (IndexBuilder index : builders.values()) {
            if (!(index.unique && index.columns.equals(primaryKey))) {
                indexes.add(index.encode());
            }
        }
        return List.copyOf(indexes);
    }

    private static boolean rowsBoolean(ResultSet rows, String column) {
        try {
            return rows.getBoolean(column);
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String rowsString(ResultSet rows, int column) {
        try {
            return rows.getString(column);
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String mysqlPortableType(String columnType) {
        String type = columnType.toLowerCase(Locale.ROOT);
        if (type.startsWith("binary(") || type.startsWith("varchar(")) {
            return type;
        }
        if (type.startsWith("bigint")) {
            return "bigint";
        }
        if (type.startsWith("tinyint")) {
            return "tinyint";
        }
        if (type.startsWith("int")) {
            return "int";
        }
        if (type.contains("text")) {
            return "large_text";
        }
        if (type.startsWith("timestamp")) {
            return "timestamp";
        }
        return type;
    }

    private static String h2PortableType(String typeName, long size) {
        String type = typeName.toUpperCase(Locale.ROOT);
        if (type.equals("BINARY") || type.equals("BINARY VARYING")) {
            return "binary(" + size + ")";
        }
        if (type.equals("CHARACTER VARYING") || type.equals("VARCHAR")) {
            return size >= 65_535 ? "large_text" : "varchar(" + size + ")";
        }
        if (type.equals("BIGINT")) {
            return "bigint";
        }
        if (type.equals("TINYINT")) {
            return "tinyint";
        }
        if (type.equals("INTEGER") || type.equals("INT")) {
            return "int";
        }
        if (type.equals("BOOLEAN") || type.equals("BIT")) {
            return "boolean";
        }
        if (type.contains("LARGE OBJECT") || type.equals("CLOB") || type.contains("TEXT")) {
            return "large_text";
        }
        if (type.startsWith("TIMESTAMP")) {
            return "timestamp";
        }
        return type.toLowerCase(Locale.ROOT) + "(" + size + ")";
    }

    private static String normalizeDefault(String value) {
        if (value == null) {
            return "<null>";
        }
        String normalized = value.strip().toUpperCase(Locale.ROOT);
        if ("NULL".equals(normalized)) {
            return "<null>";
        }
        while (normalized.startsWith("(") && normalized.endsWith(")")) {
            normalized = normalized.substring(1, normalized.length() - 1).strip();
        }
        return normalized.replace("CURRENT_TIMESTAMP()", "CURRENT_TIMESTAMP");
    }

    private static List<String> splitTopLevel(String body) {
        List<String> definitions = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int index = 0; index < body.length(); index++) {
            char character = body.charAt(index);
            if (character == '(') {
                depth++;
            } else if (character == ')') {
                depth--;
            } else if (character == ',' && depth == 0) {
                definitions.add(body.substring(start, index));
                start = index + 1;
            }
        }
        definitions.add(body.substring(start));
        return definitions;
    }

    record ExactCatalog(
            SortedMap<String, List<String>> columns,
            SortedMap<String, List<String>> indexes,
            SortedMap<String, List<String>> constraints
    ) {
        ExactCatalog {
            columns = immutableCopy(columns);
            indexes = immutableCopy(indexes);
            constraints = immutableCopy(constraints);
        }
    }

    record PortableCatalog(
            SortedMap<String, List<PortableColumn>> columns,
            SortedMap<String, List<String>> primaryKeys,
            SortedMap<String, List<String>> indexes
    ) {
        PortableCatalog {
            columns = immutableCopy(columns);
            primaryKeys = immutableCompleteCopy(columns.keySet(), primaryKeys);
            indexes = immutableCompleteCopy(columns.keySet(), indexes);
        }
    }

    record PortableColumn(
            int ordinal,
            String name,
            String type,
            boolean nullable,
            String defaultValue
    ) {
    }

    private static <T> SortedMap<String, List<T>> immutableCopy(Map<String, List<T>> source) {
        SortedMap<String, List<T>> copy = new TreeMap<>();
        source.forEach((key, value) -> copy.put(key, List.copyOf(value)));
        return Collections.unmodifiableSortedMap(copy);
    }

    private static <T> SortedMap<String, List<T>> immutableCompleteCopy(
            Set<String> tables,
            Map<String, List<T>> source
    ) {
        SortedMap<String, List<T>> copy = new TreeMap<>();
        tables.forEach(table -> copy.put(table, List.copyOf(
                source.getOrDefault(table, List.of()))));
        return Collections.unmodifiableSortedMap(copy);
    }

    private static final class IndexBuilder {
        private final String name;
        private final boolean unique;
        private final List<String> columns = new ArrayList<>();

        private IndexBuilder(String name, boolean unique) {
            this.name = name.toLowerCase(Locale.ROOT);
            this.unique = unique;
        }

        private String encode() {
            return name + ":" + (unique ? "unique:" : "index:") + String.join(",", columns);
        }
    }
}
