package com.nowcoder.community.social.api.rpc;

// social-service likes 扫描 RPC：供 content-service 回填 Redis 点赞投影使用。
import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.social.api.rpc.dto.SocialLikeScanResponse;

public interface SocialLikeScanRpcService {

    Result<SocialLikeScanResponse> scan(int entityType, long afterEntityId, long afterUserId, int limit);
}

