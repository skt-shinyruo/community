package com.nowcoder.community.message.security;

import org.springframework.util.StringUtils;

/**
 * 会话 ID 解析器（message 模块 conversationId）：
 * - 约定格式："{smallUserId}_{largeUserId}"
 * - 用于对象级鉴权（IDOR）兜底，避免仅依赖 controller 层校验
 */
public final class ConversationIdParser {

    private ConversationIdParser() {
    }

    public static ConversationMembers parseOrNull(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return null;
        }
        String cid = conversationId.trim();
        int idx = cid.indexOf('_');
        if (idx <= 0 || idx >= cid.length() - 1) {
            return null;
        }
        String a = cid.substring(0, idx).trim();
        String b = cid.substring(idx + 1).trim();
        if (!StringUtils.hasText(a) || !StringUtils.hasText(b)) {
            return null;
        }
        int ua;
        int ub;
        try {
            ua = Integer.parseInt(a);
            ub = Integer.parseInt(b);
        } catch (NumberFormatException e) {
            return null;
        }
        if (ua <= 0 || ub <= 0) {
            return null;
        }
        if (ua >= ub) {
            // conversationId 由 min/max 生成：必须严格递增；自发私信 "u_u" 不应被持久化
            return null;
        }
        return new ConversationMembers(ua, ub);
    }

    public static boolean containsUserId(String conversationId, int userId) {
        ConversationMembers members = parseOrNull(conversationId);
        return members != null && members.contains(userId);
    }

    public record ConversationMembers(int userA, int userB) {
        public boolean contains(int userId) {
            return userId == userA || userId == userB;
        }
    }
}
