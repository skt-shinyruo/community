package com.nowcoder.community.content.api.rpc;

// content-service 扫描型 RPC 接口：供 search-service 重建索引等后台任务使用。
import com.nowcoder.community.contracts.api.Result;
import com.nowcoder.community.content.api.rpc.dto.ContentPostScanResponse;

public interface ContentScanRpcService {

    Result<ContentPostScanResponse> scanPosts(int afterId, int limit);
}

