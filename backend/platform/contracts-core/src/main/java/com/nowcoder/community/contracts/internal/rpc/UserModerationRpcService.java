package com.nowcoder.community.contracts.internal.rpc;

import com.nowcoder.community.contracts.api.Result;
import com.nowcoder.community.contracts.internal.dto.UserModerationStatus;

import java.util.List;

/**
 * user 模块“治理接口”：
 * - 供下游写路径做禁言/封禁校验（fail-closed）；
 * - 供下游做投影 bootstrap/纠偏（scan + apply）。
 *
 * <p>作为 internal contract 放在 platform/contracts-core，避免 content/message 等模块直接依赖 user-api 形成编译期环。</p>
 */
public interface UserModerationRpcService {

    Result<UserModerationStatus> getStatus(int userId);

    Result<List<UserModerationStatus>> scanStatuses(int afterId, int limit);

    Result<UserModerationStatus> applyModeration(int userId, String action, int durationSeconds);
}
