package com.nowcoder.community.analytics.api.rpc;

// analytics-service 采集型内部 RPC：供 gateway 等入口服务上报 UV/DAU（best-effort，不影响主链路）。
import com.nowcoder.community.contracts.api.Result;

import java.time.LocalDate;

public interface InternalAnalyticsRpcService {

    Result<Void> recordUv(String ip, LocalDate date);

    Result<Void> recordDau(int userId, LocalDate date);
}

