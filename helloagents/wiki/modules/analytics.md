# analytics

## Purpose
提供 UV/DAU 等运营统计能力。

## Module Overview
- **Responsibility：** 记录 UV（HyperLogLog）；记录 DAU（Bitmap）；按日期区间统计；提供统计页面/接口
- **Status：** 🟡In Progress
- **Last Updated：** 2026-01-16

## Specifications

### Requirement: UV 统计
**Module:** analytics
统计指定日期范围内的 UV。

#### Scenario: 请求进入系统后记录 UV
- 以 IP 计入当日 UV

#### Scenario: 区间 UV 查询
- 合并日期范围的 UV 并返回

### Requirement: DAU 统计
**Module:** analytics
统计指定日期范围内的 DAU。

#### Scenario: 登录用户访问后记录 DAU
- 以 userId 写入当日 Bitmap

#### Scenario: 区间 DAU 查询
- 对日期范围做 OR 运算并返回 bitCount

## API Interfaces（现状）
- `GET /api/analytics/uv?start=YYYY-MM-DD&end=YYYY-MM-DD`（管理员/版主）
- `GET /api/analytics/dau?start=YYYY-MM-DD&end=YYYY-MM-DD`（管理员/版主）
- `GET /api/analytics/me`（联调用，需要登录）
- `POST /internal/analytics/uv/record`（gateway 内部调用：需要 `X-Internal-Token`）
- `POST /internal/analytics/dau/record`（gateway 内部调用：需要 `X-Internal-Token`）

## Data Models
### Redis Keys
（详见 `helloagents/wiki/data.md` 的 “Redis Key 设计” 小节）

## Dependencies
- infra（拦截器采集、Redis）

## Change History
- （暂无）
