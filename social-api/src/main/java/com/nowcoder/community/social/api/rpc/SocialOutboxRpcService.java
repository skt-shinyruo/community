package com.nowcoder.community.social.api.rpc;

import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.common.internal.dto.OutboxHealthResponse;

/**
 * social-service outbox 运维 RPC。
 */
public interface SocialOutboxRpcService {

    Result<OutboxHealthResponse> health();

    Result<Integer> replayFailed(Integer limit);
}

