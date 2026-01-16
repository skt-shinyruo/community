# Community 讨论社区（仿牛客网）

> 本文件包含项目级核心信息。详细模块说明见 `helloagents/wiki/modules/`。

---

## 1. 项目概览

### 背景与目标
该项目是一个“讨论社区”Web 应用，核心能力包括：注册/登录、发帖、评论/回复、点赞、关注、私信与系统通知、全局搜索、UV/DAU 统计、热帖分数刷新等。

当前仓库为单体 Spring Boot 应用；目标是升级到 **Spring Boot 3 + Java 17** 并拆分为多服务，同时实现 **Vue3 前后端分离**，以提升可维护性、可扩展性与独立部署能力。

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
| legacy-community | 迁移期单体模块（旧 Thymeleaf/业务逻辑承载） | ✅Stable | [legacy-community](modules/legacy-community.md) |
| frontend | Vue3 SPA（Vite + Router + Pinia + Axios） | ✅Stable | [frontend](modules/frontend.md) |

> 后续迭代将新增：`user-service`、`content-service`、`social-service`、`message-service`、`search-service`、`analytics-service`。

---

## 3. 快速链接
- [技术约定](../project.md)
- [架构设计](arch.md)
- [API 手册](api.md)
- [数据模型](data.md)
- [变更历史](../history/index.md)
