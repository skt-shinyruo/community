package com.nowcoder.community.growth.domain.service;

import org.junit.jupiter.api.Test;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;

class RewardGrantDomainServiceTest {

    private final RewardGrantDomainService service = new RewardGrantDomainService();

    @Test
    void taskGrantIdShouldBeStablePerUserTaskAndPeriod() {
        assertThat(service.taskRewardGrantId(uuid(1), "DAILY_POST", "2026-03-22"))
                .isEqualTo("task:" + uuid(1) + ":DAILY_POST:2026-03-22");
    }

    @Test
    void walletRewardAmountShouldUseBalanceDeltaOnly() {
        assertThat(service.walletRewardAmount(2)).isEqualTo(2L);
        assertThat(service.hasValidSourceEventId("event-1")).isTrue();
        assertThat(service.hasValidSourceEventId(" ")).isFalse();
    }
}
