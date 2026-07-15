package com.nowcoder.community.im.migration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

final class ImSchemaCatalog {

    static final String MANIFEST_RESOURCE =
            "db/migration/im-core/im-core-schema-manifest.tsv";

    private static final String TABLES_SQL = """
            select table_name
              from information_schema.tables
             where table_schema = ?
               and table_type = 'BASE TABLE'
             order by table_name
            """;
    private static final String COLUMNS_SQL = """
            select table_name, ordinal_position, column_name, column_type, is_nullable,
                   column_default, extra
              from information_schema.columns
             where table_schema = ?
             order by table_name, ordinal_position
            """;
    private static final String INDEXES_SQL = """
            select table_name, index_name, non_unique, seq_in_index, column_name,
                   index_type, is_visible, expression, sub_part
              from information_schema.statistics
             where table_schema = ?
             order by table_name, index_name, seq_in_index
            """;
    private static final String CONSTRAINTS_SQL = """
            select table_name, constraint_name, constraint_type, enforced
              from information_schema.table_constraints
             where constraint_schema = ?
             order by table_name, constraint_name
            """;
    private static final String KEY_COLUMNS_SQL = """
            select table_name, constraint_name, ordinal_position, column_name
              from information_schema.key_column_usage
             where constraint_schema = ?
             order by table_name, constraint_name, ordinal_position
            """;

    private final SortedMap<String, TableDefinition> tables;

    private ImSchemaCatalog(Map<String, TableDefinition> tables) {
        this.tables = Collections.unmodifiableSortedMap(new TreeMap<>(tables));
    }

    static ImSchemaCatalog capture(String jdbcUrl, String username, String password) {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            return capture(connection);
        } catch (SQLException exception) {
            throw new ImSchemaMismatchException(
                    "failed to read IM Core information_schema", exception);
        }
    }

    static ImSchemaCatalog canonical() {
        InputStream input = ImSchemaCatalog.class.getClassLoader().getResourceAsStream(MANIFEST_RESOURCE);
        if (input == null) {
            throw new IllegalStateException("missing canonical IM Core schema manifest: "
                    + MANIFEST_RESOURCE);
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            Map<String, TableDefinition> definitions = new LinkedHashMap<>();
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] fields = line.split("\\t", -1);
                if (fields.length != 4 || fields[0].isBlank()) {
                    throw new IllegalStateException(
                            "invalid IM Core schema manifest line " + lineNumber);
                }
                TableDefinition definition = new TableDefinition(
                        parseColumns(fields[1], lineNumber),
                        parseIndexes(fields[2], lineNumber),
                        parseConstraints(fields[3], lineNumber)
                );
                if (definitions.put(fields[0], definition) != null) {
                    throw new IllegalStateException(
                            "duplicate table in IM Core schema manifest: " + fields[0]);
                }
            }
            if (definitions.isEmpty()) {
                throw new IllegalStateException("canonical IM Core schema manifest is empty");
            }
            return new ImSchemaCatalog(definitions);
        } catch (IOException exception) {
            throw new IllegalStateException("cannot read canonical IM Core schema manifest", exception);
        }
    }

    ImSchemaCatalog withoutTables(Set<String> tableNames) {
        Map<String, TableDefinition> retained = new TreeMap<>(tables);
        retained.keySet().removeAll(tableNames);
        return new ImSchemaCatalog(retained);
    }

    String differenceFrom(ImSchemaCatalog expected) {
        Set<String> missing = new TreeSet<>(expected.tables.keySet());
        missing.removeAll(tables.keySet());
        Set<String> unexpected = new TreeSet<>(tables.keySet());
        unexpected.removeAll(expected.tables.keySet());
        List<String> changed = new ArrayList<>();
        for (String table : expected.tables.keySet()) {
            TableDefinition expectedDefinition = expected.tables.get(table);
            TableDefinition actualDefinition = tables.get(table);
            if (actualDefinition == null || actualDefinition.equals(expectedDefinition)) {
                continue;
            }
            List<String> facets = new ArrayList<>();
            if (!actualDefinition.columns().equals(expectedDefinition.columns())) {
                facets.add("columns/types/nullability/defaults/on-update");
            }
            if (!actualDefinition.indexes().equals(expectedDefinition.indexes())) {
                facets.add("indexes");
            }
            if (!actualDefinition.constraints().equals(expectedDefinition.constraints())) {
                facets.add("constraints");
            }
            changed.add(table + "[" + String.join(",", facets) + "]");
        }
        return "missing=" + missing + ", unexpected=" + unexpected + ", changed=" + changed;
    }

    private static ImSchemaCatalog capture(Connection connection) throws SQLException {
        String schema = connection.getCatalog();
        if (schema == null || schema.isBlank()) {
            throw new SQLException("JDBC connection has no selected database");
        }
        Map<String, TableBuilder> builders = readTables(connection, schema);
        readColumns(connection, schema, builders);
        readIndexes(connection, schema, builders);
        readConstraints(connection, schema, builders);
        readConstraintColumns(connection, schema, builders);

        Map<String, TableDefinition> definitions = new TreeMap<>();
        builders.forEach((table, builder) -> definitions.put(table, builder.build()));
        return new ImSchemaCatalog(definitions);
    }

    private static Map<String, TableBuilder> readTables(Connection connection, String schema)
            throws SQLException {
        Map<String, TableBuilder> builders = new TreeMap<>();
        try (PreparedStatement statement = connection.prepareStatement(TABLES_SQL)) {
            statement.setString(1, schema);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    builders.put(rows.getString(1), new TableBuilder());
                }
            }
        }
        return builders;
    }

    private static void readColumns(
            Connection connection,
            String schema,
            Map<String, TableBuilder> builders
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(COLUMNS_SQL)) {
            statement.setString(1, schema);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    TableBuilder builder = requireTable(builders, rows.getString("table_name"));
                    String extra = Objects.toString(rows.getString("extra"), "")
                            .toLowerCase(Locale.ROOT);
                    builder.columns.add(new ColumnDefinition(
                            rows.getInt("ordinal_position"),
                            rows.getString("column_name"),
                            normalizeType(rows.getString("column_type")),
                            "YES".equals(rows.getString("is_nullable")),
                            normalizeDefault(rows.getString("column_default")),
                            extra.contains("on update current_timestamp")
                    ));
                }
            }
        }
    }

    private static void readIndexes(
            Connection connection,
            String schema,
            Map<String, TableBuilder> builders
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INDEXES_SQL)) {
            statement.setString(1, schema);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    TableBuilder builder = requireTable(builders, rows.getString("table_name"));
                    IndexBuilder index = builder.indexes.computeIfAbsent(
                            rows.getString("index_name"),
                            ignored -> new IndexBuilder(
                                    rowsBoolean(rows, "non_unique") == false,
                                    rowsString(rows, "index_type").toUpperCase(Locale.ROOT),
                                    "YES".equalsIgnoreCase(rowsString(rows, "is_visible"))
                            )
                    );
                    index.columns.put(rows.getInt("seq_in_index"), indexColumn(rows));
                }
            }
        }
    }

    private static void readConstraints(
            Connection connection,
            String schema,
            Map<String, TableBuilder> builders
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(CONSTRAINTS_SQL)) {
            statement.setString(1, schema);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    TableBuilder builder = requireTable(builders, rows.getString("table_name"));
                    String name = rows.getString("constraint_name");
                    builder.constraints.put(name, new ConstraintBuilder(
                            normalizeConstraintType(rows.getString("constraint_type")),
                            "YES".equalsIgnoreCase(rows.getString("enforced"))
                    ));
                }
            }
        }
    }

    private static void readConstraintColumns(
            Connection connection,
            String schema,
            Map<String, TableBuilder> builders
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(KEY_COLUMNS_SQL)) {
            statement.setString(1, schema);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    TableBuilder table = requireTable(builders, rows.getString("table_name"));
                    ConstraintBuilder constraint = table.constraints.get(
                            rows.getString("constraint_name"));
                    if (constraint == null) {
                        throw new SQLException("information_schema returned an unknown constraint: "
                                + rows.getString("constraint_name"));
                    }
                    constraint.columns.put(
                            rows.getInt("ordinal_position"), rows.getString("column_name"));
                }
            }
        }
    }

    private static List<ColumnDefinition> parseColumns(String value, int lineNumber) {
        List<ColumnDefinition> columns = new ArrayList<>();
        for (String encoded : splitFacet(value)) {
            String[] fields = encoded.split(":", -1);
            if (fields.length != 6) {
                throw invalidManifestFacet("column", lineNumber, encoded);
            }
            columns.add(new ColumnDefinition(
                    parsePositiveInt(fields[0], "column ordinal", lineNumber),
                    requireManifestText(fields[1], "column name", lineNumber),
                    requireManifestText(fields[2], "column type", lineNumber),
                    parseToken(fields[3], "NULL", "NOT_NULL", "column nullability", lineNumber),
                    requireManifestText(fields[4], "column default", lineNumber),
                    parseToken(fields[5], "ON_UPDATE", "NONE", "column on-update", lineNumber)
            ));
        }
        return columns;
    }

    private static SortedMap<String, IndexDefinition> parseIndexes(String value, int lineNumber) {
        SortedMap<String, IndexDefinition> indexes = new TreeMap<>();
        for (String encoded : splitFacet(value)) {
            String[] fields = encoded.split(":", -1);
            if (fields.length != 5) {
                throw invalidManifestFacet("index", lineNumber, encoded);
            }
            String name = requireManifestText(fields[0], "index name", lineNumber);
            IndexDefinition definition = new IndexDefinition(
                    name,
                    parseToken(fields[1], "UNIQUE", "NON_UNIQUE", "index uniqueness", lineNumber),
                    requireManifestText(fields[2], "index type", lineNumber),
                    parseToken(fields[3], "VISIBLE", "INVISIBLE", "index visibility", lineNumber),
                    parseColumnList(fields[4], "index columns", lineNumber)
            );
            if (indexes.put(name, definition) != null) {
                throw new IllegalStateException(
                        "duplicate index in IM Core schema manifest line " + lineNumber + ": " + name);
            }
        }
        return indexes;
    }

    private static SortedMap<String, ConstraintDefinition> parseConstraints(String value, int lineNumber) {
        SortedMap<String, ConstraintDefinition> constraints = new TreeMap<>();
        for (String encoded : splitFacet(value)) {
            String[] fields = encoded.split(":", -1);
            if (fields.length != 4) {
                throw invalidManifestFacet("constraint", lineNumber, encoded);
            }
            String name = requireManifestText(fields[0], "constraint name", lineNumber);
            ConstraintDefinition definition = new ConstraintDefinition(
                    name,
                    requireManifestText(fields[1], "constraint type", lineNumber),
                    parseToken(fields[2], "ENFORCED", "NOT_ENFORCED", "constraint enforcement", lineNumber),
                    parseColumnList(fields[3], "constraint columns", lineNumber)
            );
            if (constraints.put(name, definition) != null) {
                throw new IllegalStateException(
                        "duplicate constraint in IM Core schema manifest line "
                                + lineNumber + ": " + name);
            }
        }
        return constraints;
    }

    private static List<String> splitFacet(String value) {
        if (value.isBlank()) {
            return List.of();
        }
        return List.of(value.split("\\|", -1));
    }

    private static List<String> parseColumnList(String value, String label, int lineNumber) {
        if (value.isBlank()) {
            return List.of();
        }
        List<String> columns = List.of(value.split(",", -1));
        if (columns.stream().anyMatch(String::isBlank)) {
            throw new IllegalStateException(
                    label + " must not contain blanks in IM Core schema manifest line " + lineNumber);
        }
        return columns;
    }

    private static boolean parseToken(
            String value,
            String trueToken,
            String falseToken,
            String label,
            int lineNumber
    ) {
        if (trueToken.equals(value)) {
            return true;
        }
        if (falseToken.equals(value)) {
            return false;
        }
        throw new IllegalStateException(label + " must be " + trueToken + " or " + falseToken
                + " in IM Core schema manifest line " + lineNumber);
    }

    private static int parsePositiveInt(String value, String label, int lineNumber) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 1) {
                throw new NumberFormatException("not positive");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(
                    label + " must be positive in IM Core schema manifest line " + lineNumber,
                    exception);
        }
    }

    private static String requireManifestText(String value, String label, int lineNumber) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    label + " must not be blank in IM Core schema manifest line " + lineNumber);
        }
        return value;
    }

    private static IllegalStateException invalidManifestFacet(
            String facet,
            int lineNumber,
            String encoded
    ) {
        return new IllegalStateException("invalid " + facet + " in IM Core schema manifest line "
                + lineNumber + ": " + encoded);
    }

    private static TableBuilder requireTable(Map<String, TableBuilder> builders, String table)
            throws SQLException {
        TableBuilder builder = builders.get(table);
        if (builder == null) {
            throw new SQLException("information_schema returned an unknown table: " + table);
        }
        return builder;
    }

    private static String indexColumn(ResultSet rows) throws SQLException {
        String expression = rows.getString("expression");
        String value = expression == null
                ? rows.getString("column_name")
                : "expression(" + expression + ")";
        int prefixLength = rows.getInt("sub_part");
        return rows.wasNull() ? value : value + "#" + prefixLength;
    }

    private static boolean rowsBoolean(ResultSet rows, String column) {
        try {
            return rows.getBoolean(column);
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String rowsString(ResultSet rows, String column) {
        try {
            return Objects.toString(rows.getString(column), "");
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String normalizeType(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static String normalizeDefault(String value) {
        if (value == null) {
            return "<null>";
        }
        String normalized = value.trim();
        if ("current_timestamp".equalsIgnoreCase(normalized)
                || "current_timestamp()".equalsIgnoreCase(normalized)) {
            return "CURRENT_TIMESTAMP";
        }
        return normalized;
    }

    private static String normalizeConstraintType(String value) {
        return value.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof ImSchemaCatalog catalog && tables.equals(catalog.tables);
    }

    @Override
    public int hashCode() {
        return tables.hashCode();
    }

    @Override
    public String toString() {
        return "ImSchemaCatalog{" + tables.size() + " tables}";
    }

    private record TableDefinition(
            List<ColumnDefinition> columns,
            SortedMap<String, IndexDefinition> indexes,
            SortedMap<String, ConstraintDefinition> constraints
    ) {
        private TableDefinition {
            columns = List.copyOf(columns);
            indexes = Collections.unmodifiableSortedMap(new TreeMap<>(indexes));
            constraints = Collections.unmodifiableSortedMap(new TreeMap<>(constraints));
        }
    }

    private record ColumnDefinition(
            int ordinal,
            String name,
            String type,
            boolean nullable,
            String defaultValue,
            boolean onUpdateCurrentTimestamp
    ) {
    }

    private record IndexDefinition(
            String name,
            boolean unique,
            String type,
            boolean visible,
            List<String> columns
    ) {
        private IndexDefinition {
            columns = List.copyOf(columns);
        }
    }

    private record ConstraintDefinition(
            String name,
            String type,
            boolean enforced,
            List<String> columns
    ) {
        private ConstraintDefinition {
            columns = List.copyOf(columns);
        }
    }

    private static final class TableBuilder {
        private final List<ColumnDefinition> columns = new ArrayList<>();
        private final Map<String, IndexBuilder> indexes = new TreeMap<>();
        private final Map<String, ConstraintBuilder> constraints = new TreeMap<>();

        private TableDefinition build() {
            Map<String, IndexDefinition> builtIndexes = new TreeMap<>();
            indexes.forEach((name, builder) -> builtIndexes.put(name, builder.build(name)));
            Map<String, ConstraintDefinition> builtConstraints = new TreeMap<>();
            constraints.forEach((name, builder) -> builtConstraints.put(name, builder.build(name)));
            return new TableDefinition(columns, new TreeMap<>(builtIndexes), new TreeMap<>(builtConstraints));
        }
    }

    private static final class IndexBuilder {
        private final boolean unique;
        private final String type;
        private final boolean visible;
        private final SortedMap<Integer, String> columns = new TreeMap<>();

        private IndexBuilder(boolean unique, String type, boolean visible) {
            this.unique = unique;
            this.type = type;
            this.visible = visible;
        }

        private IndexDefinition build(String name) {
            return new IndexDefinition(name, unique, type, visible, List.copyOf(columns.values()));
        }
    }

    private static final class ConstraintBuilder {
        private final String type;
        private final boolean enforced;
        private final SortedMap<Integer, String> columns = new TreeMap<>();

        private ConstraintBuilder(String type, boolean enforced) {
            this.type = type;
            this.enforced = enforced;
        }

        private ConstraintDefinition build(String name) {
            return new ConstraintDefinition(name, type, enforced, List.copyOf(columns.values()));
        }
    }
}
