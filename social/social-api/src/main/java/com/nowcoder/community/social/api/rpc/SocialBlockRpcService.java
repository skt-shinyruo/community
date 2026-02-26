package com.nowcoder.community.social.api.rpc;

// social-service 拉黑关系 RPC：供 content-service/message-service 写路径校验使用。
import com.nowcoder.community.contracts.api.Result;

public interface SocialBlockRpcService {

    Result<Boolean> isEitherBlocked(int userIdA, int userIdB);
}

