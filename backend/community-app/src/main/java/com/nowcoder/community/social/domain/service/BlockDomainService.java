package com.nowcoder.community.social.domain.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.social.domain.event.BlockRelationChangedDomainEvent;
import com.nowcoder.community.social.domain.repository.BlockRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.social.exception.SocialErrorCode.CANNOT_BLOCK_SELF;

@Service
public class BlockDomainService {

    public void validateBlock(UUID actorUserId, UUID targetUserId) {
        validateUserIds(actorUserId, targetUserId);
        if (actorUserId.equals(targetUserId)) {
            throw new BusinessException(CANNOT_BLOCK_SELF);
        }
    }

    public void validateUnblock(UUID actorUserId, UUID targetUserId) {
        validateUserIds(actorUserId, targetUserId);
    }

    public boolean isEitherBlocked(UUID userIdA, UUID userIdB, BlockRepository repository) {
        if (userIdA == null || userIdB == null || repository == null) {
            return false;
        }
        if (userIdA.equals(userIdB)) {
            return false;
        }
        return repository.hasBlocked(userIdA, userIdB) || repository.hasBlocked(userIdB, userIdA);
    }

    public BlockRelationChangedDomainEvent blockChangedEvent(UUID actorUserId, UUID targetUserId, boolean blocked) {
        return new BlockRelationChangedDomainEvent(actorUserId, targetUserId, blocked);
    }

    private void validateUserIds(UUID actorUserId, UUID targetUserId) {
        if (actorUserId == null || targetUserId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "userId/targetUserId 非法");
        }
    }
}
