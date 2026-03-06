package com.nowcoder.community.social.application;

import com.nowcoder.community.social.block.BlockService;
import org.springframework.stereotype.Service;

@Service
public class BlockQueryApplicationService {

    private final BlockService blockService;

    public BlockQueryApplicationService(BlockService blockService) {
        this.blockService = blockService;
    }

    public boolean isEitherBlocked(int userIdA, int userIdB) {
        if (userIdA <= 0 || userIdB <= 0 || userIdA == userIdB) {
            return false;
        }
        return blockService.isEitherBlocked(userIdA, userIdB);
    }
}
