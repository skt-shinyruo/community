package com.nowcoder.community.wallet.service;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.wallet.dto.CreateRechargeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class RechargeServiceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RechargeService rechargeService;

    @Autowired
    private WalletAccountService accountService;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from reward_ledger");
        jdbcTemplate.update("delete from reward_account");
        jdbcTemplate.update("delete from wallet_entry");
        jdbcTemplate.update("delete from wallet_txn");
        jdbcTemplate.update("delete from recharge_order");
        jdbcTemplate.update("delete from withdraw_order");
        jdbcTemplate.update("delete from wallet_account");
    }

    @Test
    void completeRechargeShouldCreditUserWalletOnce() {
        CreateRechargeResponse result = rechargeService.complete("recharge:req-1", 101, 1200);

        assertThat(result.status()).isEqualTo("PAID");
        assertThat(accountService.balanceOfUser(101)).isEqualTo(1200);
    }
}
