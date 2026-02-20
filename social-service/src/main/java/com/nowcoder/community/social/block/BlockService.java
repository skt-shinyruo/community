// 拉黑业务服务：实现拉黑/解除拉黑/状态查询/内部关系查询（用于反骚扰与写路径校验）。
package com.nowcoder.community.social.block;

import com.nowcoder.community.social.api.event.payload.BlockPayload;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.social.event.SocialEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.nowcoder.community.common.api.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.social.api.SocialErrorCode.CANNOT_BLOCK_SELF;

@Service
public class BlockService {

    private final BlockRepository repository;
    private final SocialEventPublisher eventPublisher;

    public BlockService(BlockRepository repository, SocialEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void block(int userId, int targetUserId) {
        if (userId <= 0 || targetUserId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "userId/targetUserId 非法");
        }
        if (userId == targetUserId) {
            throw new BusinessException(CANNOT_BLOCK_SELF);
        }
        repository.block(userId, targetUserId);

        BlockPayload payload = new BlockPayload();
        payload.setBlockerUserId(userId);
        payload.setBlockedUserId(targetUserId);
        payload.setBlocked(Boolean.TRUE);
        eventPublisher.publishBlockRelationChanged(payload);
    }

    @Transactional
    public void unblock(int userId, int targetUserId) {
        if (userId <= 0 || targetUserId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "userId/targetUserId 非法");
        }
        repository.unblock(userId, targetUserId);

        BlockPayload payload = new BlockPayload();
        payload.setBlockerUserId(userId);
        payload.setBlockedUserId(targetUserId);
        payload.setBlocked(Boolean.FALSE);
        eventPublisher.publishBlockRelationChanged(payload);
    }

    public boolean hasBlocked(int userId, int targetUserId) {
        if (userId <= 0 || targetUserId <= 0) {
            return false;
        }
        return repository.hasBlocked(userId, targetUserId);
    }

    public boolean isEitherBlocked(int userIdA, int userIdB) {
        if (userIdA <= 0 || userIdB <= 0) {
            return false;
        }
        if (userIdA == userIdB) {
            return false;
        }
        return repository.hasBlocked(userIdA, userIdB) || repository.hasBlocked(userIdB, userIdA);
    }

    public List<Integer> listBlockedUserIds(int userId) {
        if (userId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        return repository.listBlockedUserIds(userId);
    }
}
