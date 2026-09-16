package com.nowcoder.community.ops.application;

/**
 * Shared string escaping for the hand-built governance audit detail JSON.
 */
final class GovernanceAuditJson {

    private GovernanceAuditJson() {
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
