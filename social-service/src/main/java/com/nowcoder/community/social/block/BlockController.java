// 拉黑 API：登录用户对其他用户拉黑/解除拉黑，并查询拉黑列表与状态。
package com.nowcoder.community.social.block;

import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.common.exception.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.nowcoder.community.common.api.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.common.api.CommonErrorCode.UNAUTHORIZED;

@RestController
@RequestMapping("/api/blocks")
public class BlockController {

    private final BlockService blockService;

    public BlockController(BlockService blockService) {
        this.blockService = blockService;
    }

    @PostMapping
    public Result<Void> block(Authentication authentication, @Valid @RequestBody BlockRequest request) {
        int userId = currentUserId(authentication);
        blockService.block(userId, request.getUserId());
        return Result.ok();
    }

    @DeleteMapping
    public Result<Void> unblock(Authentication authentication, @RequestParam int userId) {
        int actorId = currentUserId(authentication);
        blockService.unblock(actorId, userId);
        return Result.ok();
    }

    @GetMapping
    public Result<List<Integer>> list(Authentication authentication) {
        int userId = currentUserId(authentication);
        return Result.ok(blockService.listBlockedUserIds(userId));
    }

    @GetMapping("/status")
    public Result<Boolean> status(Authentication authentication, @RequestParam int userId) {
        int actorId = currentUserId(authentication);
        return Result.ok(blockService.hasBlocked(actorId, userId));
    }

    private int currentUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new BusinessException(UNAUTHORIZED, "未获取到认证信息");
        }
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String sub = jwt.getSubject();
        try {
            return Integer.parseInt(sub);
        } catch (NumberFormatException e) {
            throw new BusinessException(INVALID_ARGUMENT, "token subject 非法");
        }
    }

    public static class BlockRequest {
        @NotNull
        @Min(1)
        private Integer userId;

        public Integer getUserId() {
            return userId;
        }

        public void setUserId(Integer userId) {
            this.userId = userId;
        }
    }
}
