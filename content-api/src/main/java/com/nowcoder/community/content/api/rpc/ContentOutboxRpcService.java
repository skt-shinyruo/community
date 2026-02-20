package com.nowcoder.community.content.api.rpc;

import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.common.internal.dto.OutboxHealthResponse;

/**
 * content-service outbox 运维 RPC。
 *
 * <p>说明：用于 gateway `/api/ops/**` 统一触发，不应暴露为 internal HTTP。</p>
 */
public interface ContentOutboxRpcService {

    Result<OutboxHealthResponse> health();

    Result<Integer> replayFailed(Integer limit);
}

