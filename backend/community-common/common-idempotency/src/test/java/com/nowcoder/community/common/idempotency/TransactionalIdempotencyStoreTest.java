package com.nowcoder.community.common.idempotency;

import com.nowcoder.community.common.exception.ErrorKind;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class TransactionalIdempotencyStoreTest {

    @Test
    void shouldReportWhetherItsDataSourceIsEnlistedInTheCurrentTransaction() {
        DataSource storeDataSource = dataSource("store");
        DataSource otherDataSource = dataSource("other");
        TransactionalIdempotencyStore store = new JdbcIdempotencyStore(new JdbcTemplate(storeDataSource));

        assertThat(store.isEnlistedInCurrentTransaction()).isFalse();

        new TransactionTemplate(new DataSourceTransactionManager(storeDataSource)).executeWithoutResult(status ->
                assertThat(store.isEnlistedInCurrentTransaction()).isTrue()
        );
        new TransactionTemplate(new DataSourceTransactionManager(otherDataSource)).executeWithoutResult(status ->
                assertThat(store.isEnlistedInCurrentTransaction()).isFalse()
        );
    }

    @Test
    void errorCodesShouldExposeConflictAndUnavailableSemantics() {
        assertAll(
                () -> assertThat(IdempotencyErrorCode.IDEMPOTENCY_IN_PROGRESS.getCode()).isEqualTo(409),
                () -> assertThat(IdempotencyErrorCode.IDEMPOTENCY_IN_PROGRESS.getKind())
                        .isEqualTo(ErrorKind.CONFLICT),
                () -> assertThat(IdempotencyErrorCode.IDEMPOTENCY_OUTCOME_INDETERMINATE.getCode()).isEqualTo(409),
                () -> assertThat(IdempotencyErrorCode.IDEMPOTENCY_OUTCOME_INDETERMINATE.getKind())
                        .isEqualTo(ErrorKind.CONFLICT),
                () -> assertThat(IdempotencyErrorCode.IDEMPOTENCY_OUTCOME_INDETERMINATE.getMessage())
                        .contains("结果不确定，请查询业务状态")
                        .doesNotContain("换 key", "更换 key", "更换幂等键"),
                () -> assertThat(IdempotencyErrorCode.IDEMPOTENCY_STORE_UNAVAILABLE.getCode()).isEqualTo(503),
                () -> assertThat(IdempotencyErrorCode.IDEMPOTENCY_STORE_UNAVAILABLE.getKind())
                        .isEqualTo(ErrorKind.UNAVAILABLE)
        );
    }

    private DataSource dataSource(String name) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:transactional-idempotency-" + name + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        return dataSource;
    }
}
