package com.nowcoder.community.migration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

final class CommunitySchemaCatalog {

    static final String MANIFEST_RESOURCE = "db/migration/community/community-schema-manifest.tsv";

    private static final String TABLES_SQL = """
            select table_name
              from information_schema.tables
             where table_schema = ?
               and table_type = 'BASE TABLE'
             order by table_name
            """;

    private static final String COLUMNS_SQL = """
            select table_name, ordinal_position, column_name, column_type, is_nullable,
                   column_default, extra, character_set_name, collation_name, generation_expression
              from information_schema.columns
             where table_schema = ?
             order by table_name, ordinal_position
            """;

    private static final String INDEXES_SQL = """
            select table_name, index_name, non_unique, seq_in_index, column_name, collation,
                   sub_part, packed, nullable, index_type, comment, index_comment, is_visible, expression
              from information_schema.statistics
             where table_schema = ?
             order by table_name, index_name, seq_in_index
            """;

    private static final String TABLE_CONSTRAINTS_SQL = """
            select table_name, constraint_name, constraint_type, enforced
              from information_schema.table_constraints
             where constraint_schema = ?
             order by table_name, constraint_name
            """;

    private static final String KEY_COLUMNS_SQL = """
            select table_name, constraint_name, ordinal_position, position_in_unique_constraint,
                   column_name, referenced_table_name, referenced_column_name
              from information_schema.key_column_usage
             where constraint_schema = ?
             order by table_name, constraint_name, ordinal_position
            """;

    private static final String REFERENTIAL_CONSTRAINTS_SQL = """
            select table_name, constraint_name, unique_constraint_name, match_option, update_rule, delete_rule
              from information_schema.referential_constraints
             where constraint_schema = ?
             order by table_name, constraint_name
            """;

    private static final String CHECK_CONSTRAINTS_SQL = """
            select tc.table_name, cc.constraint_name, cc.check_clause
              from information_schema.check_constraints cc
              join information_schema.table_constraints tc
                on tc.constraint_schema = cc.constraint_schema
               and tc.constraint_name = cc.constraint_name
             where cc.constraint_schema = ?
             order by tc.table_name, cc.constraint_name
            """;

    private final SortedMap<String, TableFingerprint> tables;

    private CommunitySchemaCatalog(Map<String, TableFingerprint> tables) {
        this.tables = Collections.unmodifiableSortedMap(new TreeMap<>(tables));
    }

    static CommunitySchemaCatalog capture(String jdbcUrl, String username, String password) {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            return capture(connection);
        } catch (SQLException exception) {
            throw new CommunitySchemaMismatchException("failed to read Community information_schema", exception);
        }
    }

    static CommunitySchemaCatalog canonical() {
        InputStream input = CommunitySchemaCatalog.class.getClassLoader().getResourceAsStream(MANIFEST_RESOURCE);
        if (input == null) {
            throw new IllegalStateException("missing canonical Community schema manifest: " + MANIFEST_RESOURCE);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            Map<String, TableFingerprint> fingerprints = new LinkedHashMap<>();
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] fields = line.split("\\t", -1);
                if (fields.length != 4 || fields[0].isBlank()) {
                    throw new IllegalStateException("invalid Community schema manifest line " + lineNumber);
                }
                TableFingerprint previous = fingerprints.put(fields[0],
                        new TableFingerprint(fields[1], fields[2], fields[3]));
                if (previous != null) {
                    throw new IllegalStateException("duplicate table in Community schema manifest: " + fields[0]);
                }
            }
            if (fingerprints.isEmpty()) {
                throw new IllegalStateException("canonical Community schema manifest is empty");
            }
            return new CommunitySchemaCatalog(fingerprints);
        } catch (IOException exception) {
            throw new IllegalStateException("cannot read canonical Community schema manifest", exception);
        }
    }

    CommunitySchemaCatalog withoutTables(Set<String> tableNames) {
        Map<String, TableFingerprint> retained = new TreeMap<>(tables);
        retained.keySet().removeAll(tableNames);
        return new CommunitySchemaCatalog(retained);
    }

    String differenceFrom(CommunitySchemaCatalog expected) {
        Set<String> missing = new TreeSet<>(expected.tables.keySet());
        missing.removeAll(tables.keySet());
        Set<String> unexpected = new TreeSet<>(tables.keySet());
        unexpected.removeAll(expected.tables.keySet());
        List<String> changed = new ArrayList<>();
        for (String table : expected.tables.keySet()) {
            TableFingerprint expectedFingerprint = expected.tables.get(table);
            TableFingerprint actualFingerprint = tables.get(table);
            if (actualFingerprint == null || actualFingerprint.equals(expectedFingerprint)) {
                continue;
            }
            List<String> facets = new ArrayList<>();
            if (!actualFingerprint.columnsSha256().equals(expectedFingerprint.columnsSha256())) {
                facets.add("columns/defaults");
            }
            if (!actualFingerprint.indexesSha256().equals(expectedFingerprint.indexesSha256())) {
                facets.add("indexes");
            }
            if (!actualFingerprint.constraintsSha256().equals(expectedFingerprint.constraintsSha256())) {
                facets.add("constraints");
            }
            changed.add(table + "[" + String.join(",", facets) + "]");
        }
        return "missing=" + missing + ", unexpected=" + unexpected + ", changed=" + changed;
    }

    private static CommunitySchemaCatalog capture(Connection connection) throws SQLException {
        String schema = connection.getCatalog();
        if (schema == null || schema.isBlank()) {
            throw new SQLException("JDBC connection has no selected database");
        }

        Map<String, FingerprintBuilder> builders = new TreeMap<>();
        try (PreparedStatement statement = connection.prepareStatement(TABLES_SQL)) {
            statement.setString(1, schema);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    builders.put(rows.getString(1), new FingerprintBuilder());
                }
            }
        }
        appendRows(connection, schema, COLUMNS_SQL, builders, Facet.COLUMNS, "COLUMN", 2, 10);
        appendRows(connection, schema, INDEXES_SQL, builders, Facet.INDEXES, "INDEX", 2, 14);
        appendRows(connection, schema, TABLE_CONSTRAINTS_SQL, builders, Facet.CONSTRAINTS, "TABLE_CONSTRAINT", 2, 4);
        appendRows(connection, schema, KEY_COLUMNS_SQL, builders, Facet.CONSTRAINTS, "KEY_COLUMN", 2, 7);
        appendRows(connection, schema, REFERENTIAL_CONSTRAINTS_SQL, builders, Facet.CONSTRAINTS,
                "REFERENTIAL_CONSTRAINT", 2, 6);
        appendRows(connection, schema, CHECK_CONSTRAINTS_SQL, builders, Facet.CONSTRAINTS, "CHECK_CONSTRAINT", 2, 3);

        Map<String, TableFingerprint> fingerprints = new TreeMap<>();
        builders.forEach((table, builder) -> fingerprints.put(table, builder.build()));
        return new CommunitySchemaCatalog(fingerprints);
    }

    private static void appendRows(
            Connection connection,
            String schema,
            String sql,
            Map<String, FingerprintBuilder> builders,
            Facet facet,
            String rowType,
            int firstValueColumn,
            int lastValueColumn
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, schema);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    FingerprintBuilder builder = builders.get(rows.getString(1));
                    if (builder == null) {
                        throw new SQLException("information_schema returned an unknown table: " + rows.getString(1));
                    }
                    StringBuilder encoded = new StringBuilder(rowType);
                    for (int column = firstValueColumn; column <= lastValueColumn; column++) {
                        appendField(encoded, rows.getString(column), rows.wasNull());
                    }
                    builder.add(facet, encoded.toString());
                }
            }
        }
    }

    private static void appendField(StringBuilder target, String value, boolean sqlNull) {
        target.append('|');
        if (sqlNull) {
            target.append("-1:");
        } else {
            target.append(value.length()).append(':').append(value);
        }
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof CommunitySchemaCatalog catalog && tables.equals(catalog.tables);
    }

    @Override
    public int hashCode() {
        return tables.hashCode();
    }

    @Override
    public String toString() {
        return "CommunitySchemaCatalog{" + tables.size() + " tables}";
    }

    private enum Facet {
        COLUMNS,
        INDEXES,
        CONSTRAINTS
    }

    private static final class FingerprintBuilder {
        private final List<String> columns = new ArrayList<>();
        private final List<String> indexes = new ArrayList<>();
        private final List<String> constraints = new ArrayList<>();

        void add(Facet facet, String row) {
            switch (facet) {
                case COLUMNS -> columns.add(row);
                case INDEXES -> indexes.add(row);
                case CONSTRAINTS -> constraints.add(row);
            }
        }

        TableFingerprint build() {
            return new TableFingerprint(hash(columns), hash(indexes), hash(constraints));
        }

        private static String hash(List<String> rows) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                for (String row : rows) {
                    digest.update(row.getBytes(StandardCharsets.UTF_8));
                    digest.update((byte) '\n');
                }
                return java.util.HexFormat.of().formatHex(digest.digest());
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 is unavailable", exception);
            }
        }
    }

    private record TableFingerprint(
            String columnsSha256,
            String indexesSha256,
            String constraintsSha256
    ) {
        private TableFingerprint {
            Objects.requireNonNull(columnsSha256, "columnsSha256");
            Objects.requireNonNull(indexesSha256, "indexesSha256");
            Objects.requireNonNull(constraintsSha256, "constraintsSha256");
        }
    }
}
