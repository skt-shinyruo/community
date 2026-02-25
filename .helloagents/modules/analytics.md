# analytics

## Purpose
提供 UV/DAU 等运营统计能力。

## Module Overview
- **Responsibility：** 记录 UV（HyperLogLog）；记录 DAU（Bitmap）；按日期区间统计；提供统计页面/接口
- **Status：** 🟡In Progress
- **Last Updated：** 2026-02-13

## Specifications

### Requirement: UV 统计
**Module:** analytics
统计指定日期范围内的 UV。

#### Scenario: 请求进入系统后记录 UV
- 以 IP 计入当日 UV

#### Scenario: 区间 UV 查询
- 合并日期范围的 UV 并返回
- 区间 unionKey 为临时 key（随机后缀），查询结束后 delete；异常/进程崩溃时短 TTL 兜底，避免 Redis key/内存膨胀

### Requirement: DAU 统计
**Module:** analytics
统计指定日期范围内的 DAU。

#### Scenario: 登录用户访问后记录 DAU
- 以 userId 写入当日 Bitmap

#### Scenario: 区间 DAU 查询
- 对日期范围做 OR 运算并返回 bitCount
- BITOP OR 的 unionKey 同样为临时 key（随机后缀），查询结束后 delete；异常/进程崩溃时短 TTL 兜底，避免 Redis key/内存膨胀

## API Interfaces（现状）
- `GET /api/analytics/uv?start=YYYY-MM-DD&end=YYYY-MM-DD`（管理员/版主）
- `GET /api/analytics/dau?start=YYYY-MM-DD&end=YYYY-MM-DD`（管理员/版主）
- `GET /api/analytics/me`（联调用，需要登录）
- Dubbo RPC（服务间同步调用，推荐）：`analytics-api` 的 `InternalAnalyticsRpcService`（gateway 采集 best-effort 调用）
- 说明：当前版本不再提供 HTTP `/internal/**` 写入口；采集链路统一由 gateway 通过 Dubbo RPC 调用。

> 说明：gateway 侧采集链路应做到“不影响主业务链路”，并保证可观测（超时/并发限制/降噪/指标）。gateway → analytics-service 的内部调用建议透传 `X-Trace-Id/traceparent` 便于排障。

## Data Models
### Redis Keys
（详见 `.helloagents/data.md` 的 “Redis Key 设计” 小节）

## Dependencies
- infra（拦截器采集、Redis）

## Change History
- 2026-02-04：区间 UV/DAU 查询使用临时 unionKey（delete + 短 TTL 兜底），避免 Redis key/内存膨胀。
- 2026-02-13：移除 HTTP `/internal/analytics/**` 写入口，采集链路统一由 gateway 通过 Dubbo RPC 调用 analytics-service。
