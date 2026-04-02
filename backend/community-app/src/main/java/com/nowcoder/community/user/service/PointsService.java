package com.nowcoder.community.user.service;

import com.nowcoder.community.user.api.action.UserPointsActionApi;
import com.nowcoder.community.user.mapper.UserMapper;
import com.nowcoder.community.user.mapper.UserScoreLogMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@Service
public class PointsService implements UserPointsActionApi {

    public static final int LEVEL_SCORE_STEP = 100;

    private final UserMapper userMapper;
    private final UserScoreLogMapper userScoreLogMapper;

    public PointsService(UserMapper userMapper, UserScoreLogMapper userScoreLogMapper) {
        this.userMapper = userMapper;
        this.userScoreLogMapper = userScoreLogMapper;
    }

    public int levelForScore(int score) {
        int s = Math.max(0, score);
        return (s / LEVEL_SCORE_STEP) + 1;
    }

    /**
     * 旧成长值入账（幂等）。
     *
     * <p>钱包切换后，该服务仅保留给显式旧成长值管理路径使用；实时奖励写路径不再调用这里。</p>
     *
     * @return true 表示首次入账成功；false 表示已处理过（重复事件）
     */
    @Transactional
    @Override
    public boolean applyPoints(int userId, String eventId, String eventType, int delta) {
        if (userId <= 0) {
            throw new com.nowcoder.community.common.exception.BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        if (eventId == null || eventId.isBlank()) {
            throw new com.nowcoder.community.common.exception.BusinessException(INVALID_ARGUMENT, "eventId 缺失");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new com.nowcoder.community.common.exception.BusinessException(INVALID_ARGUMENT, "eventType 缺失");
        }
        if (delta == 0) {
            return false;
        }

        try {
            userScoreLogMapper.insert(userId, eventId, eventType, delta);
        } catch (DataIntegrityViolationException e) {
            // 唯一约束冲突（重复消费/重试） -> 幂等返回
            return false;
        }

        int updated = userMapper.addScore(userId, delta);
        if (updated <= 0) {
            throw new IllegalStateException("积分入账失败: userId=" + userId);
        }
        return true;
    }
}
