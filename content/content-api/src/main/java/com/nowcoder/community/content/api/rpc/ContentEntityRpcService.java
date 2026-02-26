package com.nowcoder.community.content.api.rpc;

// content-service 实体解析 RPC：供下游在写路径构造可信 payload（禁止信任客户端注入）。
import com.nowcoder.community.contracts.api.Result;
import com.nowcoder.community.contracts.internal.dto.EntityResolveResponse;

public interface ContentEntityRpcService {

    Result<EntityResolveResponse> resolveEntity(int entityType, int entityId);
}

