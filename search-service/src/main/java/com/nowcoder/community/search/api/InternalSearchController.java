package com.nowcoder.community.search.api;

import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.common.api.CommonErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.search.api.dto.ReindexResponse;
import com.nowcoder.community.search.service.PostSearchService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.util.StringUtils;

@RestController
@RequestMapping("/internal/search")
public class InternalSearchController {

    private final PostSearchService postSearchService;
    private final String internalToken;

    public InternalSearchController(PostSearchService postSearchService, @Value("${search.internal-token:}") String internalToken) {
        this.postSearchService = postSearchService;
        this.internalToken = internalToken;
    }

    @PostMapping("/reindex")
    public Result<ReindexResponse> reindex(@RequestHeader(name = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        int count = postSearchService.clearAndReindexFromContentService();
        return Result.ok(new ReindexResponse(count));
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
