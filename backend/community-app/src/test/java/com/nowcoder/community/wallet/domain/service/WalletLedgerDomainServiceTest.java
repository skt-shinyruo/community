package com.nowcoder.community.wallet.domain.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.wallet.domain.model.WalletPosting;
import com.nowcoder.community.wallet.domain.model.WalletTxnType;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletLedgerDomainServiceTest {

    private final WalletLedgerDomainService service = new WalletLedgerDomainService();

    @Test
    void validateBalancedPostingsShouldAcceptEqualDebitAndCredit() {
        assertThatCode(() -> service.validateBalancedPostings(List.of(
                WalletPosting.debit(uuid(1), 100),
                WalletPosting.credit(uuid(2), 100)
        ))).doesNotThrowAnyException();
    }

    @Test
    void validateBalancedPostingsShouldRejectUnbalancedEntries() {
        assertThatThrownBy(() -> service.validateBalancedPostings(List.of(
                WalletPosting.debit(uuid(1), 100),
                WalletPosting.credit(uuid(2), 90)
        )))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(WalletErrorCode.TXN_NOT_BALANCED));
    }

    @Test
    void newTxnShouldCreatePendingTransaction() {
        UUID txnId = uuid(11);
        Date createdAt = new Date(1000);

        var txn = service.newTxn(txnId, "request-1", WalletTxnType.TRANSFER, "TRANSFER", "biz-1", 300, createdAt);

        assertThat(txn.getTxnId()).isEqualTo(txnId);
        assertThat(txn.getRequestId()).isEqualTo("request-1");
        assertThat(txn.getTxnType()).isEqualTo("TRANSFER");
        assertThat(txn.getBizType()).isEqualTo("TRANSFER");
        assertThat(txn.getBizId()).isEqualTo("biz-1");
        assertThat(txn.getAmount()).isEqualTo(300);
        assertThat(txn.getStatus()).isEqualTo("PENDING");
        assertThat(txn.getCreateTime()).isEqualTo(createdAt);
    }
}
