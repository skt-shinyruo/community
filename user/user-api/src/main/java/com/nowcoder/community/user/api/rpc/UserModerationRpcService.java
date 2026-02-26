package com.nowcoder.community.user.api.rpc;

// user-service 治理接口 RPC：供下游构建本地投影与写路径校验使用（禁言/封禁等）。
import com.nowcoder.community.contracts.api.Result;
import com.nowcoder.community.user.api.rpc.dto.UserModerationStatus;

import java.util.List;

public interface UserModerationRpcService {

    Result<UserModerationStatus> getStatus(int userId);

    Result<List<UserModerationStatus>> scanStatuses(int afterId, int limit);

    Result<UserModerationStatus> applyModeration(int userId, String action, int durationSeconds);
}

