package com.nowcoder.community.user.api.rpc;

// user-service 读取型 RPC 接口：供 message-service/content-service 等服务聚合展示使用，避免 HTTP fan-out。
import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.user.api.rpc.dto.UserSummary;

import java.util.List;

public interface UserReadRpcService {

    Result<UserSummary> resolveByUsernameOrNull(String username);

    Result<UserSummary> getByIdOrNull(int userId);

    Result<List<UserSummary>> batchSummary(List<Integer> userIds);
}

