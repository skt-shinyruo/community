package com.nowcoder.community.search.api;

import com.nowcoder.community.contracts.api.Result;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * legacy 运维入口：
 * - 历史对外路径：POST /api/search/internal/reindex
 * - 现行路径：POST /api/ops/search/reindex（由 ops 平面承载）
 *
 * <p>该 endpoint 不再承载功能语义，仅用于返回 410 + successor link，避免误用与攻击面回潮。</p>
 */
@RestController
@RequestMapping("/api/search/internal")
public class SearchLegacyController {

    private static final String SUCCESSOR = "/api/ops/search/reindex";

    @PostMapping("/reindex")
    public ResponseEntity<Result<Void>> legacyReindex() {
        return ResponseEntity.status(HttpStatus.GONE)
                // RFC 8288 Web Linking: successor-version is defined for versioning; here we just provide a successor link.
                .header("Link", "<" + SUCCESSOR + ">; rel=\"successor\"")
                .header("X-Successor", SUCCESSOR)
                .body(Result.error(HttpStatus.GONE.value(), "已迁移至 " + SUCCESSOR));
    }
}

