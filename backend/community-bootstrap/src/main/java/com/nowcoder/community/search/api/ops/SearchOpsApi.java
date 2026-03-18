package com.nowcoder.community.search.api.ops;

import com.nowcoder.community.contracts.api.Result;
import com.nowcoder.community.search.api.ops.dto.SearchReindexResponse;

/**
 * Search 模块运维接口：
 * - reindex 等高成本动作只应通过受控入口触发（例如 `/api/ops/**`）。
 */
public interface SearchOpsApi {

    Result<SearchReindexResponse> reindex();
}
