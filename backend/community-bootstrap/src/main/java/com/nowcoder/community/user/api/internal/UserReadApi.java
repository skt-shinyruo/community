package com.nowcoder.community.user.api.internal;

import com.nowcoder.community.contracts.api.Result;
import com.nowcoder.community.user.api.internal.dto.UserSummary;

import java.util.List;

/**
 * user 模块对内读取接口：
 * - 供 message/content 等模块做聚合展示；
 * - 统一提供用户摘要/批量摘要/用户名解析等只读能力。
 */
public interface UserReadApi {

    Result<UserSummary> resolveByUsernameOrNull(String username);

    Result<UserSummary> getByIdOrNull(int userId);

    Result<List<UserSummary>> batchSummary(List<Integer> userIds);
}
