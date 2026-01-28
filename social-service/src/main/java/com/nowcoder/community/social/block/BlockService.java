// 拉黑业务服务：实现拉黑/解除拉黑/状态查询/内部关系查询（用于反骚扰与写路径校验）。
package com.nowcoder.community.social.block;

import com.nowcoder.community.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.nowcoder.community.common.api.CommonErrorCode.INVALID_ARGUMENT;

@Service
public class BlockService {

    private final BlockRepository repository;

    public BlockService(BlockRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void block(int userId, int targetUserId) {
        if (userId <= 0 || targetUserId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "userId/targetUserId 非法");
        }
        if (userId == targetUserId) {
            throw new BusinessException(INVALID_ARGUMENT, "不能拉黑自己");
        }
        repository.block(userId, targetUserId);
    }

    @Transactional
    public void unblock(int userId, int targetUserId) {
        if (userId <= 0 || targetUserId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "userId/targetUserId 非法");
        }
        repository.unblock(userId, targetUserId);
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
