// 内部拉黑关系查询：用于 content/message 写路径校验（依赖 X-Internal-Token）。
package com.nowcoder.community.social.block;

import com.nowcoder.community.common.api.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/social/blocks")
public class InternalBlockController {

    private final BlockService blockService;

    public InternalBlockController(BlockService blockService) {
        this.blockService = blockService;
    }

    @GetMapping("/relation")
    public Result<Boolean> relation(
            @RequestParam int userIdA,
            @RequestParam int userIdB
    ) {
        return Result.ok(blockService.isEitherBlocked(userIdA, userIdB));
    }
}
