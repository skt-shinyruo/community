package com.nowcoder.community.search.api.rpc;

import com.nowcoder.community.contracts.api.Result;
import com.nowcoder.community.search.api.rpc.dto.SearchReindexResponse;

/**
 * search-service 运维类 RPC：
 * - reindex 等高成本动作只应通过受控入口触发（例如 gateway `/api/ops/**`）。
 */
public interface SearchOpsRpcService {

    Result<SearchReindexResponse> reindex();
}

