package com.nowcoder.community.content.domain.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.domain.model.ModerationDecision;
import org.junit.jupiter.api.Test;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModerationDecisionDomainServiceTest {

    private final ModerationDecisionDomainService service = new ModerationDecisionDomainService();

    @Test
    void decideShouldNormalizeActionReasonAndResolveDurations() {
        ModerationDecision mute = service.decide(uuid(1), uuid(2), " MUTE ", " spam ", null);
        ModerationDecision ban = service.decide(uuid(1), uuid(2), "ban", "abuse", -1);
        ModerationDecision warn = service.decide(uuid(1), uuid(2), "warn", "abuse", -1);

        assertThat(mute.normalizedAction()).isEqualTo("mute");
        assertThat(mute.normalizedReason()).isEqualTo("spam");
        assertThat(mute.resolvedDurationSeconds()).isEqualTo(86400);
        assertThat(ban.resolvedDurationSeconds()).isEqualTo(604800);
        assertThat(warn.resolvedDurationSeconds()).isZero();
    }

    @Test
    void decideShouldRejectInvalidCommand() {
        assertThatThrownBy(() -> service.decide(null, uuid(2), "warn", "reason", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("actorId 非法");
        assertThatThrownBy(() -> service.decide(uuid(1), null, "warn", "reason", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("reportId 非法");
        assertThatThrownBy(() -> service.decide(uuid(1), uuid(2), "noop", "reason", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("action 非法");
        assertThatThrownBy(() -> service.decide(uuid(1), uuid(2), "warn", " ", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("reason 不能为空");
    }
}
