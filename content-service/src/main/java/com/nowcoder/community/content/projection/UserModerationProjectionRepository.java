package com.nowcoder.community.content.projection;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * content-service 本地投影仓库（最终一致）：
 * - 用户处罚状态（mute/ban）
 * - 拉黑关系（block/unblock）
 *
 * <p>注意：该仓库只维护“读模型”，SSOT 分别在 user-service/social-service。</p>
 */
@Repository
public class UserModerationProjectionRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserModerationProjectionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void upsertModerationStatus(int userId, Instant muteUntil, Instant banUntil, Instant updatedAt) {
        int uid = Math.max(0, userId);
        if (uid <= 0 || updatedAt == null) {
            return;
        }
        jdbcTemplate.update(
                """
                        insert into user_moderation_projection(user_id, mute_until, ban_until, updated_at)
                        values (?, ?, ?, ?)
                        on duplicate key update
                          mute_until = if(values(updated_at) >= updated_at, values(mute_until), mute_until),
                          ban_until = if(values(updated_at) >= updated_at, values(ban_until), ban_until),
                          updated_at = greatest(updated_at, values(updated_at))
                        """,
                uid,
                muteUntil == null ? null : Timestamp.from(muteUntil),
                banUntil == null ? null : Timestamp.from(banUntil),
                Timestamp.from(updatedAt)
        );
    }

    public ModerationSnapshot findModerationStatus(int userId) {
        int uid = Math.max(0, userId);
        if (uid <= 0) {
            return null;
        }
        List<ModerationSnapshot> list = jdbcTemplate.query(
                "select user_id, mute_until, ban_until, updated_at from user_moderation_projection where user_id = ?",
                (rs, rowNum) -> {
                    ModerationSnapshot s = new ModerationSnapshot();
                    s.setUserId(rs.getInt("user_id"));
                    Timestamp mu = rs.getTimestamp("mute_until");
                    Timestamp bu = rs.getTimestamp("ban_until");
                    Timestamp ua = rs.getTimestamp("updated_at");
                    s.setMuteUntil(mu == null ? null : mu.toInstant());
                    s.setBanUntil(bu == null ? null : bu.toInstant());
                    s.setUpdatedAt(ua == null ? null : ua.toInstant());
                    return s;
                },
                uid
        );
        return list == null || list.isEmpty() ? null : list.get(0);
    }

    public void upsertBlockRelation(int blockerUserId, int blockedUserId, boolean blocked, Instant updatedAt) {
        int a = Math.max(0, blockerUserId);
        int b = Math.max(0, blockedUserId);
        if (a <= 0 || b <= 0 || a == b || updatedAt == null) {
            return;
        }
        jdbcTemplate.update(
                """
                        insert into user_block_projection(blocker_user_id, blocked_user_id, blocked, updated_at)
                        values (?, ?, ?, ?)
                        on duplicate key update
                          blocked = if(values(updated_at) >= updated_at, values(blocked), blocked),
                          updated_at = greatest(updated_at, values(updated_at))
                        """,
                a,
                b,
                blocked ? 1 : 0,
                Timestamp.from(updatedAt)
        );
    }

    public boolean isEitherBlocked(int userIdA, int userIdB) {
        int a = Math.max(0, userIdA);
        int b = Math.max(0, userIdB);
        if (a <= 0 || b <= 0 || a == b) {
            return false;
        }
        List<Integer> ab = jdbcTemplate.queryForList(
                "select blocked from user_block_projection where blocker_user_id = ? and blocked_user_id = ?",
                Integer.class,
                a,
                b
        );
        if (!ab.isEmpty() && ab.get(0) == 1) {
            return true;
        }
        List<Integer> ba = jdbcTemplate.queryForList(
                "select blocked from user_block_projection where blocker_user_id = ? and blocked_user_id = ?",
                Integer.class,
                b,
                a
        );
        return !ba.isEmpty() && ba.get(0) == 1;
    }

    public void assertNotBlocked(int userIdA, int userIdB) {
        if (isEitherBlocked(userIdA, userIdB)) {
            throw new com.nowcoder.community.common.exception.BusinessException(
                    com.nowcoder.community.common.api.CommonErrorCode.FORBIDDEN,
                    "双方存在拉黑关系，无法执行该操作"
            );
        }
    }

    public void assertCanSpeak(int userId) {
        ModerationSnapshot s = findModerationStatus(userId);
        if (s == null || s.getUpdatedAt() == null) {
            // 投影缺失时不做“猜测”，交由上层决定是否 fail-closed（503）或降级处理。
            throw new com.nowcoder.community.common.exception.BusinessException(
                    com.nowcoder.community.common.api.CommonErrorCode.SERVICE_UNAVAILABLE,
                    "处罚状态投影缺失"
            );
        }

        Instant now = Instant.now();
        if (s.getBanUntil() != null && s.getBanUntil().isAfter(now)) {
            throw new com.nowcoder.community.common.exception.BusinessException(
                    com.nowcoder.community.common.api.CommonErrorCode.FORBIDDEN,
                    "账号已被封禁，无法发言"
            );
        }
        if (s.getMuteUntil() != null && s.getMuteUntil().isAfter(now)) {
            throw new com.nowcoder.community.common.exception.BusinessException(
                    com.nowcoder.community.common.api.CommonErrorCode.FORBIDDEN,
                    "你已被禁言，暂时无法发言"
            );
        }
    }

    public static class ModerationSnapshot {
        private int userId;
        private Instant muteUntil;
        private Instant banUntil;
        private Instant updatedAt;

        public int getUserId() {
            return userId;
        }

        public void setUserId(int userId) {
            this.userId = userId;
        }

        public Instant getMuteUntil() {
            return muteUntil;
        }

        public void setMuteUntil(Instant muteUntil) {
            this.muteUntil = muteUntil;
        }

        public Instant getBanUntil() {
            return banUntil;
        }

        public void setBanUntil(Instant banUntil) {
            this.banUntil = banUntil;
        }

        public Instant getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
        }
    }
}
