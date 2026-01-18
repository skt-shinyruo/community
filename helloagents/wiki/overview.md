# Community 讨论社区（仿牛客网）

> 本文件包含项目级核心信息。详细模块说明见 `helloagents/wiki/modules/`。

---

## 1. 项目概览

### 背景与目标
该项目是一个“讨论社区”Web 应用，核心能力包括：注册/登录、发帖、评论/回复、点赞、关注、私信与系统通知、全局搜索、UV/DAU 统计、热帖分数刷新等。

当前仓库同时包含：
- **legacy-community（旧单体源码）**：仅保留源码用于对照与迁移参考（不再纳入 docker compose 部署与切流）。
- **目标态微服务体系（gateway + 多服务 + Vue3）**：作为生产交付目标，支持全依赖（Nacos/MySQL/Redis/Kafka/Elasticsearch）、可观测（Metrics/Logs/Trace）、灰度/回滚与备份/恢复。

目标是：在完成 **旧单体 100% 功能等价** 与生产级运维能力补齐后，逐步 **下线 legacy-community（仅保留源码存档）**。

### Scope
- **In scope：**
  - 账号体系（注册/激活/登录/鉴权）
  - 内容域（帖子/评论/回复/敏感词过滤/热帖）
  - 社交域（点赞/关注/粉丝）
  - 消息域（私信/系统通知）
  - 搜索与统计（ES 搜索、UV/DAU）
- **Out of scope：**
  - 支付、电商、强一致金融类交易
  - 复杂权限模型（RBAC 多租户等）

### Stakeholders
- **Owner：** TBD

---

## 2. 模块索引（当前仓库）

| Module Name | Responsibility | Status | Documentation |
|-------------|----------------|--------|---------------|
| common | 统一 Result/错误码/异常处理/traceId | ✅Stable | [common](modules/common.md) |
| gateway | 统一入口：路由/CORS/鉴权/trace/错误收敛 | ✅Stable | [gateway](modules/gateway.md) |
| auth-service | 登录/刷新/登出闭环（JWT + Redis refresh rotation） | ✅Stable | [auth-service](modules/auth-service.md) |
| search-service | 搜索服务（Kafka 消费 + ES 索引 + 高亮 + reindex） | ✅Stable | [search](modules/search.md) |
| message-service | 通知/私信服务（Kafka 消费 + MySQL，含聚合接口） | ✅Stable | [message](modules/message.md) |
| analytics-service | 统计服务（UV/DAU，Redis；网关采集写入） | ✅Stable | [analytics](modules/analytics.md) |
| user-service | 用户资料与头像（Qiniu） | ✅Stable | [user](modules/user.md) |
| content-service | 帖子/评论/热帖/敏感词过滤（Kafka + Redis + MySQL） | ✅Stable | [content](modules/content.md) |
| social-service | 点赞/关注/粉丝（Redis + Kafka） | ✅Stable | [social](modules/social.md) |
| legacy-community | 旧单体源码（仅对照/迁移参考，不部署） | 🟡Reference | [legacy-community](modules/legacy-community.md) |
| frontend | Vue3 SPA（Vite + Router + Pinia + Axios） | ✅Stable | [frontend](modules/frontend.md) |

> 注：生产级交付相关操作入口见：`deploy/`、`scripts/`、`helloagents/wiki/runbooks/`。

---

## 3. 快速链接
- [技术约定](../project.md)
- [架构设计](arch.md)
- [API 手册](api.md)
- [数据模型](data.md)
- [运行手册：docker compose 启动与回归](runbooks/legacy-cutover.md)
- [变更历史](../history/index.md)
