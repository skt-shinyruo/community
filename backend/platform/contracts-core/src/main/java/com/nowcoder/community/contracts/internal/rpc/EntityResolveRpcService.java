package com.nowcoder.community.contracts.internal.rpc;

import com.nowcoder.community.contracts.api.Result;
import com.nowcoder.community.contracts.internal.dto.EntityResolveResponse;

/**
 * 内部“实体解析”接口：用于在写路径构造可信 payload（禁止信任客户端注入 entityUserId / postId）。
 * <p>
 * 说明：
 * - entityType/entityId 是通用概念（POST/COMMENT 等），实体的 SSOT 仍由对应领域模块维护；
 * - 调用方应 fail-closed：解析失败即拒绝后续写入，避免脏数据。
 */
public interface EntityResolveRpcService {

    Result<EntityResolveResponse> resolveEntity(int entityType, int entityId);
}
