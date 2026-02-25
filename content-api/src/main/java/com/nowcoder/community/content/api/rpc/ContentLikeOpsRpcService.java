package com.nowcoder.community.content.api.rpc;

import com.nowcoder.community.contracts.api.Result;
import com.nowcoder.community.content.api.rpc.dto.ContentLikeBackfillResponse;

/**
 * content-service 点赞投影运维 RPC：回填 Redis 投影（cold start mitigation）。
 */
public interface ContentLikeOpsRpcService {

    Result<ContentLikeBackfillResponse> backfill(int entityType, Long maxItems, Integer batchSize);
}

