# infra

## Purpose
提供跨模块的基础设施能力：配置、拦截器、AOP、定时任务、监控与通用工具等。

## Module Overview
- **Responsibility：**
  - Web 拦截器：登录态注入、消息未读数、UV/DAU 采集
  - 安全：Spring Security 授权配置、权限不足处理
  - 事件：Kafka 生产/消费封装
  - 定时任务：Quartz 刷新帖子分数
  - 监控：Actuator + 自定义端点（数据库连接检查）
  - 工具：敏感词过滤、JSON 工具、Cookie 工具、Redis key 工具等
- **Status：** ✅Stable
- **Last Updated：** 2026-01-16

## Specifications

### Requirement: 权限控制与授权
**Module:** infra
对不同接口路径做权限控制（普通用户/版主/管理员）。

#### Scenario: 未登录访问受保护资源
- Ajax 请求返回 403 JSON
- 页面请求重定向到登录页

### Requirement: 异步事件解耦
**Module:** infra
行为事件通过 Kafka 解耦通知与索引更新。

#### Scenario: 发帖触发索引更新
- 生产 publish 事件
- search 模块消费并写入 ES

### Requirement: 定时刷新帖子分数
**Module:** infra
周期刷新帖子 score，并同步搜索索引。

#### Scenario: Quartz 任务执行刷新
- 从 Redis 集合取出待刷新帖子
- 计算 score 并更新 DB + ES

## API Interfaces
- 管理接口：`/data/**`、`/actuator/**`（管理员权限）

## Dependencies
- 全模块共用（Redis/Kafka/Quartz/Security/Actuator）

## Change History
- （暂无）

