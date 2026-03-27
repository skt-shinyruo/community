package com.nowcoder.community.ops.controller;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.exception.ErrorCode;
import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.search.dto.SearchReindexResponse;
import com.nowcoder.community.search.service.SearchAdminService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运维平面 API：
 * - 对外路径：/api/ops/**
 * - 内部调用：进程内 Spring Bean
 *
 * <p>说明：该服务用于隔离高风险/高成本运维能力，降低 edge gateway 的爆炸半径。</p>
 */
@RestController
@RequestMapping("/api/ops")
public class OpsController {

    private final SearchAdminService searchAdminService;

    public OpsController(SearchAdminService searchAdminService) {
        this.searchAdminService = searchAdminService;
    }

    @PostMapping("/search/reindex")
    public ResponseEntity<Result<SearchReindexResponse>> reindex() {
        try {
            return ResponseEntity.ok(Result.ok(searchAdminService.reindex()));
        } catch (BusinessException e) {
            return businessError(e);
        }
    }

    private ResponseEntity<Result<SearchReindexResponse>> businessError(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode() == null ? CommonErrorCode.INTERNAL_ERROR : e.getErrorCode();
        String message = e.getMessage() == null ? errorCode.getMessage() : e.getMessage();
        return ResponseEntity.status(httpStatusOf(errorCode.getHttpStatus()))
                .body(Result.error(errorCode.getCode(), message, errorCode.getHttpStatus()));
    }

    private HttpStatus httpStatusOf(int httpStatus) {
        try {
            return HttpStatus.valueOf(httpStatus);
        } catch (IllegalArgumentException ignored) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }
}
