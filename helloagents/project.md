# 项目技术约定（SSOT）

> 本文件定义项目的技术栈与协作约定。项目级文档入口见 `helloagents/wiki/overview.md`。

---

## 1. 技术栈

### 1.1 当前现状（基于代码扫描）
- **后端：** Spring Boot 2.x（仓库当前 `pom.xml` 为 2.1.5.RELEASE）+ Spring MVC + Thymeleaf
- **语言：** Java 8（仓库当前 `pom.xml` 指定）
- **持久化：** MyBatis + MySQL
- **缓存：** Redis（点赞/关注、登录票据 ticket、验证码、UV/DAU、帖子分数刷新集合等）
- **消息：** Kafka（评论/点赞/关注/发帖/删帖事件）
- **搜索：** Elasticsearch（帖子全文检索 + 高亮）
- **定时：** Quartz（刷新帖子分数）
- **安全：** Spring Security（授权），自定义 ticket 认证注入 `SecurityContext`
- **其他：** Caffeine（帖子列表/总数本地缓存）、七牛云（头像上传）、Actuator（健康检查/自定义端点）

### 1.2 目标态（迁移方向）
- **后端：** Spring Boot 3.x + Spring Cloud（微服务）+ Spring Cloud Alibaba Nacos（注册发现/配置中心）
- **语言：** Java 17
- **前端：** Vue 3（前后端分离，SPA）
- **鉴权：** JWT Access Token + Refresh Token（推荐旋转刷新）
- **API：** RESTful JSON，统一返回结构与错误码

---

## 2. 工程与模块约定

### 2.1 微服务命名与边界
- 服务按领域拆分：`gateway`、`auth-service`、`user-service`、`content-service`、`social-service`、`message-service`、`search-service`、`analytics-service`
- **原则：** 一个服务拥有自己的数据归属与演进节奏；跨服务通过 API 或事件交互，禁止跨库 JOIN。

### 2.2 配置管理
- 所有环境配置以 Nacos 为准，禁止把密钥/Token/账号密码写入代码库。
- 配置按环境隔离（dev/test/prod），并保持可本地启动的最小配置集（可用 mock/本地 docker compose 支撑）。

---

## 3. API 与错误处理约定

### 3.1 统一返回结构（建议）
- 统一返回：`code` / `message` / `data` / `traceId`
- 错误码按模块分段（例如：`AUTH_****`、`USER_****`、`CONTENT_****`）

### 3.2 全局异常处理
- 由 `@RestControllerAdvice` 统一收敛异常，禁止在 Controller 中返回拼接字符串 JSON。
- 对外仅暴露稳定错误码与可读 message，敏感堆栈只写日志。

---

## 4. 日志与可观测性

### 4.1 Trace 约定
- Gateway 生成并透传 `traceId`（例如 header `X-Request-Id`），各服务日志必须输出该字段。

### 4.2 日志规范
- 统一采用结构化日志或固定格式日志（至少包含：时间、等级、服务名、traceId、用户标识、关键业务字段）。

---

## 5. 测试与交付

### 5.1 测试分层
- **单元测试：** 核心领域逻辑（Service、工具类）。
- **集成测试：** 数据库/Redis/Kafka/ES 关键链路，建议使用 Testcontainers 或 docker compose。
- **契约测试：** 服务间 API 契约（推荐逐步引入）。

### 5.2 交付与回滚
- 每个服务独立构建与部署；灰度/回滚由 Gateway 路由策略支持。

