# Community 讨论社区（仿牛客网）

> 本文件包含项目级核心信息。详细模块说明见 `helloagents/wiki/modules/`。

---

## 1. 项目概览

### 背景与目标
该项目是一个“讨论社区”Web 应用，核心能力包括：注册/登录、发帖、评论/回复、点赞、关注、私信与系统通知、全局搜索、UV/DAU 统计、热帖分数刷新等。

当前仓库以 **微服务 + 前后端分离** 为唯一主路径：
- **后端：** `gateway` + 各领域 `*-service`
- **前端：** Vue3 SPA（`frontend/`）
- **本地环境：** `deploy/` 目录提供 docker compose 一键拉起全依赖与全链路

> 说明：历史单体（迁移期模块）已从仓库主干移除；历史决策/迁移记录保留在 `helloagents/history/`，用于追溯与对照。

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
| auth-service | 登录/刷新/登出闭环（JWT + refresh rotation） | ✅Stable | [auth-service](modules/auth-service.md) |
| user-service | 用户资料与头像（Qiniu） | ✅Stable | [user](modules/user.md) |
| content-service | 帖子/评论/热帖/敏感词过滤（Kafka + Redis + MySQL） | ✅Stable | [content](modules/content.md) |
| social-service | 点赞/关注/粉丝（Redis + Kafka） | ✅Stable | [social](modules/social.md) |
| message-service | 通知/私信服务（Kafka 消费 + MySQL，含聚合接口） | ✅Stable | [message](modules/message.md) |
| search-service | 搜索服务（Kafka 消费 + ES 索引 + 高亮 + reindex） | ✅Stable | [search](modules/search.md) |
| analytics-service | 统计服务（UV/DAU，Redis；网关采集写入） | ✅Stable | [analytics](modules/analytics.md) |
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
