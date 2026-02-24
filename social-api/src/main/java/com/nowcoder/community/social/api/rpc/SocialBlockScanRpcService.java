package com.nowcoder.community.social.api.rpc;

import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.social.api.rpc.dto.SocialBlockScanResponse;

/**
 * social-service 拉黑关系扫描 RPC（internal）：
 * - 供下游服务冷启动/补洞回填 user_block_projection 使用
 * - 使用 keyset 分页，避免 offset 扫描退化
 */
public interface SocialBlockScanRpcService {

    Result<SocialBlockScanResponse> scan(int afterBlockerUserId, int afterBlockedUserId, int limit);
}

