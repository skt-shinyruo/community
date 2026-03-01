package com.nowcoder.community.user.api.rpc;

import com.nowcoder.community.contracts.api.Result;
import com.nowcoder.community.contracts.internal.dto.OutboxHealthResponse;

/**
 * user-service outbox 运维 RPC。
 */
public interface UserOutboxRpcService {

    Result<OutboxHealthResponse> health();

    Result<Integer> replayFailed(Integer limit);
}

