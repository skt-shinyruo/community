// 内部拉黑关系查询：用于 content/message 写路径校验（依赖 X-Internal-Token）。
package com.nowcoder.community.social.block;

import com.nowcoder.community.common.api.CommonErrorCode;
import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/social/blocks")
public class InternalBlockController {

    private final BlockService blockService;
    private final String internalToken;

    public InternalBlockController(BlockService blockService, @Value("${social.internal-token:}") String internalToken) {
        this.blockService = blockService;
        this.internalToken = internalToken;
    }

    @GetMapping("/relation")
    public Result<Boolean> relation(
            @RequestHeader(name = "X-Internal-Token", required = false) String token,
            @RequestParam int userIdA,
            @RequestParam int userIdB
    ) {
        assertInternalToken(token);
        return Result.ok(blockService.isEitherBlocked(userIdA, userIdB));
    }

    private void assertInternalToken(String token) {
        if (!StringUtils.hasText(internalToken)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "internal-token 未配置");
        }
        if (!StringUtils.hasText(token) || !internalToken.equals(token)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "internal-token 无效");
        }
    }
}

