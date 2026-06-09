package com.nowcoder.observability.runtimediagnostics.probes.jdbc;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcStatementAdviceTest {

    @Test
    void normalizesSqlOperationAndHashWithoutBindValues() {
        JdbcStatementAdvice.JdbcCall call = JdbcStatementAdvice.describeSql(
                "select * from user where password = 'secret'");

        assertThat(call.operation()).isEqualTo("select");
        assertThat(call.statementHash()).hasSize(16);
        assertThat(call.statementHash()).doesNotContain("secret");
    }

    @Test
    void unknownSqlUsesUnknownOperation() {
        JdbcStatementAdvice.JdbcCall call = JdbcStatementAdvice.describeSql("");

        assertThat(call.operation()).isEqualTo("unknown");
        assertThat(call.statementHash()).isEqualTo("unknown");
    }
}
