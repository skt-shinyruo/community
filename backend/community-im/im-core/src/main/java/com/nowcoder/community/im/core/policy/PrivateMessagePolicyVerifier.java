package com.nowcoder.community.im.core.policy;

import com.nowcoder.community.im.common.policy.PrivateMessagePolicyDecision;

import java.util.UUID;

public interface PrivateMessagePolicyVerifier {

    PrivateMessagePolicyDecision verify(UUID fromUserId, UUID toUserId);

    static void requireAllowed(PrivateMessagePolicyDecision decision) {
        if (decision == null) {
            throw new IllegalStateException("private message policy decision unavailable");
        }
        if (!decision.allowed()) {
            throw new PrivateMessagePolicyRejectedException(
                    decision.code(),
                    decision.reasonCode(),
                    decision.message()
            );
        }
    }

    final class PrivateMessagePolicyRejectedException extends SecurityException {

        private final int code;
        private final String reasonCode;

        public PrivateMessagePolicyRejectedException(int code, String reasonCode, String message) {
            super(message == null || message.isBlank() ? "private message denied" : message);
            this.code = code <= 0 ? 403 : code;
            this.reasonCode = reasonCode == null || reasonCode.isBlank() ? "policy_denied" : reasonCode;
        }

        public int code() {
            return code;
        }

        public String reasonCode() {
            return reasonCode;
        }
    }
}
