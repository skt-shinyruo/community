# Change Proposal: legacy 下线生产级收尾（100% 功能等价）

## Requirement Background

当前仓库已完成“微服务骨架 + Vue3 + 基础 API/规范”的阶段性改造，但旧单体 `legacy-community` 仍承载了大量用户可见功能与页面路由（注册/激活/验证码、帖子管理与审核、个人设置、站内信与通知、统计页面等）。  
如果要达到“**生产级可交付**、且 **可以不再部署 legacy-community**”的目标，需要把旧单体现有功能 **100% 等价迁移** 到新体系（gateway + 各服务 + 前端），并补齐生产运维能力：全依赖可用（Nacos/MySQL/Redis/Kafka/Elasticsearch）、监控与告警、日志/Trace 追踪、灰度/回滚、数据迁移与备份、权限审计、限流风控、容量与压测指标、配置/密钥管理规范。

## Product Analysis

### Target Users and Scenarios
- **User Groups：** 最终用户（社区访问与发帖互动）、项目维护者/开发者、运维/发布人员
- **Usage Scenarios：**
  - 生产/准生产环境用 docker compose 一键拉起全栈（含观测与告警）
  - 以旧单体为验收基准做全量回归，完成“可下线 legacy”切换
  - 发生故障时可快速定位（指标/日志/追踪）并具备回滚开关
- **Core Pain Points：**
  - 当前新微服务仅覆盖部分 REST API，旧页面与部分业务仍依赖 legacy
  - Nacos/Kafka/ES 等全依赖链路缺乏端到端验证与可观测性
  - 缺少生产级交付要素（灰度/回滚/备份/压测/告警/密钥管理）

### Value Proposition and Success Metrics
- **Value Proposition：** 以“旧单体 100% 功能等价”为硬约束，交付可观测、可回滚、可演练的微服务体系，并最终下线 legacy。
- **Success Metrics：**
  - 功能等价：旧单体核心功能与边角功能均可在新体系中完成（覆盖率 100%）
  - 可下线：生产部署不再包含 `legacy-community`，且 gateway 不再依赖 legacy 路由
  - 全依赖：Nacos/MySQL/Redis/Kafka/ES 全部启用并在集成验证中真实接入
  - 可观测：服务健康、关键指标、错误率、延迟、Kafka/ES 关键链路可观测并有告警
  - 可回滚：一次性切换后仍有可操作的回滚手段（快速恢复到可用状态）

### Humanistic Care
在迁移与演练过程中避免暴露敏感信息（邮箱、token、密钥），对用户的功能可用性保持连续，出现故障时优先保障“可登录/可浏览/可发帖”主链路。

## Change Content
1. 建立“旧单体 → 新体系”的功能对齐矩阵与验收用例矩阵（作为切换门禁）
2. 以 Big-bang 模式补齐缺失功能与 API/页面能力，达到 100% 等价
3. 全依赖接入与端到端回归：Nacos/MySQL/Redis/Kafka/Elasticsearch 全链路可用
4. 生产级交付能力：观测与告警、灰度/回滚、备份与恢复、权限审计、限流风控、容量与压测
5. 完成切换与 legacy 下线：网关路由/前端入口/运维文档/CI 门禁全部对齐

## Impact Scope
- **Modules：**
  - gateway
  - auth-service / user-service / content-service / social-service / message-service / search-service / analytics-service
  - deploy（docker compose + 配置示例 + 脚本）
  - legacy-community（最终下线/移除运行时依赖）
- **Files：**
  - 新增/调整：各服务 API/DTO/Service/安全配置/事件消费生产/ES 索引等
  - 新增：docker compose 全栈（含观测与告警）与发布/回滚/备份脚本
  - 新增：功能对齐矩阵与验收用例矩阵（文档 + 自动化测试）
- **APIs：**
  - 补齐旧单体所有用户可见能力（注册/激活/验证码、帖子管理、个人设置、私信/通知、统计、搜索高亮、管理/审核）
  - 保持对外 API 一致的错误语义与鉴权语义
- **Data：**
  - MySQL：用户/帖子/评论/消息/通知等表结构与数据迁移策略
  - Redis：会话/验证码/统计/限流等 key 归属与清理策略
  - Kafka：事件 topic 权限、schema 版本化与回放策略
  - Elasticsearch：索引模板、mapping、reindex 与重建策略

## Core Scenarios

### Requirement: 用户注册激活与验证码
**Module:** auth-service / user-service

#### Scenario: 注册-激活-登录闭环
前置条件：邮件服务/配置可用（或可替代为测试模式），验证码服务可用
- 用户可注册并收到激活方式（邮件/链接）
- 激活后可正常登录并获得 access token + refresh cookie
- 登录失败/爆破触发限流与验证码策略

### Requirement: 帖子/评论/热帖与审核能力
**Module:** content-service / gateway

#### Scenario: 发帖-评论-热帖-置顶/加精/删除
前置条件：内容过滤与权限体系可用
- 普通用户可发帖与评论/回复
- 管理员/版主可对帖子置顶/加精/删除，行为与旧单体一致
- 热帖排序与分数刷新与旧单体一致

### Requirement: 点赞/关注与通知联动
**Module:** social-service / message-service / content-service

#### Scenario: 点赞/关注触发通知
前置条件：Kafka 可用，消息消费可用
- 点赞/取消点赞状态与计数正确
- 关注/取消关注正确，列表分页正确
- 点赞/评论/关注产生通知，未读数与旧单体一致

### Requirement: 私信与会话列表
**Module:** message-service / user-service

#### Scenario: 发送私信-会话聚合-已读标记
前置条件：用户查询可用
- 可按用户名发送私信（对齐旧单体交互）
- 会话列表展示：最新消息、会话消息数、未读数、对端用户信息
- 私信详情分页与已读逻辑与旧单体一致

### Requirement: 搜索（ES）与索引一致性
**Module:** search-service / content-service

#### Scenario: 发帖后可搜索命中并高亮
前置条件：Elasticsearch 可用，索引模板已创建
- 发帖后通过事件或回源写入 ES
- 搜索支持分页与高亮字段，效果与旧单体一致
- 提供可运维的 reindex/索引重建能力

### Requirement: 统计（UV/DAU）与采集链路
**Module:** analytics-service / gateway

#### Scenario: 访问采集-区间统计查询
前置条件：Redis 可用，采集逻辑启用
- UV/DAU 采集与旧单体口径一致
- 支持按日期区间查询 UV/DAU

### Requirement: 生产级交付（观测/告警/灰度回滚/备份/压测/密钥）
**Module:** deploy / gateway / all services

#### Scenario: docker compose 一键拉起 + 全链路可观测 + 可回滚
前置条件：所有依赖启用并可用
- `docker compose up -d` 可启动全套基础设施 + 微服务 + 观测组件
- 指标/日志/追踪可串联定位问题并产生告警
- 有明确的切换与回滚步骤（可演练）
- 数据具备备份与恢复演练手册

## Risk Assessment
- **Risk：** Big-bang 交付周期长、一次性风险高  
  **Mitigation：** 以功能对齐矩阵驱动，分域完成但“最终切换一次性”；在切换前完成全量回归与演练。
- **Risk：** Kafka/ES/数据一致性问题导致线上不可用  
  **Mitigation：** 事件契约与幂等、可回放；ES 可重建；关键链路做降级与熔断策略。
- **Risk：** 权限与管理操作边界不清带来安全风险  
  **Mitigation：** 网关 + 服务双层校验、审计日志、最小权限、内部接口 token。
- **Risk：** 缺少可观测与压测导致容量不可控  
  **Mitigation：** 指标与告警先行；压测脚本与容量基线纳入发布门禁。

## Success Metrics

> 说明：以下为“发布门禁”与“目标阈值”定义。不同机器/容器资源/网络条件会影响结果，实际容量基线应在目标部署形态上重复测量并记录。

### 1) 压测覆盖范围（门禁）
- 登录：`POST /api/auth/login` + `GET /api/auth/me`
- 发帖：`POST /api/posts` + `GET /api/posts`
- 点赞：`POST /api/likes`（toggle 语义）
- 搜索：`GET /api/search/posts`
- 私信：`POST /api/messages`

脚本入口：见 `loadtest/k6/community-baseline.js` 与 `loadtest/README.md`。

### 2) 建议阈值（用于告警/放量门禁）

以 **网关** 维度统计（建议同时关注下游服务 p95/p99）：

- `http_req_failed`（错误率）：`< 1%`
- 登录（login）：p95 `< 500ms`，p99 `< 1000ms`
- 发帖（post-create）：p95 `< 800ms`，p99 `< 1500ms`
- 点赞（like）：p95 `< 300ms`，p99 `< 800ms`
- 搜索（search）：p95 `< 800ms`，p99 `< 1500ms`
- 私信（message-send）：p95 `< 500ms`，p99 `< 1000ms`

### 3) 容量基线记录（需要在目标环境实测后补齐）

建议在目标部署形态（同等资源配额）下记录：
- 并发（VUs）：[?]
- 持续时间：[?]
- 吞吐（RPS）：[测得值]
- p95/p99（按场景）：[测得值]
- 错误率（按场景）：[测得值]
- 资源消耗：CPU/内存/GC/连接数/Kafka lag/ES 查询失败率：[测得值]
