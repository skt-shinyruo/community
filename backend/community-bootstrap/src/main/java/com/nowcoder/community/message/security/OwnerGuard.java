package com.nowcoder.community.message.security;

import com.nowcoder.community.contracts.api.CommonErrorCode;
import com.nowcoder.community.contracts.api.ErrorCode;
import com.nowcoder.community.contracts.exception.BusinessException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 对象级鉴权断言（OwnerGuard）：
 * - 统一抛出语义化错误（默认 404，避免泄露资源存在性）
 * - 统一指标埋点（invalid/mismatch），便于观测潜在攻击与误用
 */
@Component
public class OwnerGuard {

    private static final String METRIC = "owner_guard_total";

    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    public OwnerGuard(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.meterRegistryProvider = meterRegistryProvider;
    }

    public void assertConversationMember(int userId, String conversationId) {
        assertConversationMember(userId, conversationId, CommonErrorCode.NOT_FOUND);
    }

    public void assertConversationMember(int userId, String conversationId, ErrorCode notFoundErrorCode) {
        ErrorCode notFound = notFoundErrorCode == null ? CommonErrorCode.NOT_FOUND : notFoundErrorCode;
        if (userId <= 0) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "userId 非法");
        }
        if (!StringUtils.hasText(conversationId)) {
            record("conversation", "invalid_conversation_id");
            throw new BusinessException(notFound, "会话不存在");
        }

        ConversationIdParser.ConversationMembers members = ConversationIdParser.parseOrNull(conversationId);
        if (members == null) {
            record("conversation", "invalid_conversation_id");
            throw new BusinessException(notFound, "会话不存在");
        }
        if (!members.contains(userId)) {
            record("conversation", "owner_mismatch");
            throw new BusinessException(notFound, "会话不存在");
        }
    }

    private void record(String type, String outcome) {
        MeterRegistry meterRegistry = meterRegistryProvider == null ? null : meterRegistryProvider.getIfAvailable();
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.counter(METRIC, Tags.of("type", type, "outcome", outcome)).increment();
    }
}
