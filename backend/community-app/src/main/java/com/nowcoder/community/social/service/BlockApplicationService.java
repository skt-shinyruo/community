package com.nowcoder.community.social.service;

import com.nowcoder.community.social.block.BlockService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class BlockApplicationService {

    private final BlockService blockService;

    public BlockApplicationService(BlockService blockService) {
        this.blockService = blockService;
    }

    public void block(UUID userId, UUID targetUserId) {
        blockService.block(userId, targetUserId);
    }

    public void unblock(UUID userId, UUID targetUserId) {
        blockService.unblock(userId, targetUserId);
    }

    public List<UUID> listBlockedUserIds(UUID userId) {
        return blockService.listBlockedUserIds(userId);
    }

    public boolean hasBlocked(UUID userId, UUID targetUserId) {
        return blockService.hasBlocked(userId, targetUserId);
    }
}
