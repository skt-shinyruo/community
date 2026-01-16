# 变更提案：Boot 3 + Java 17 + Vue3 + Nacos 微服务化拆分

## Requirement Background

当前仓库为单体社区应用，业务域覆盖用户、帖子、评论、点赞、关注、私信/通知、搜索与统计等。单体形态在功能扩展、独立部署与故障隔离方面存在天然瓶颈，同时 Spring Boot 2.x 与 Java 8 生态逐步老化。

本提案目标是：**必须升级到 Spring Boot 3.x + Java 17**，并在此基础上进行微服务化拆分，最终实现 **Vue3 前后端分离**，以提升可维护性、可扩展性与演进效率。

## Product Analysis

### Target Users and Scenarios
- **User Groups：** 项目维护者/开发者、后续贡献者、学习与演示使用者
- **Usage Scenarios：**
  - 新功能快速迭代（模块独立部署）
  - 高并发热点能力独立扩容（点赞/关注/搜索/消息）
  - 前后端独立开发与发布（Vue3 SPA）
- **Core Pain Points：**
  - 单体耦合度高，改动牵一发而动全身
  - 发布/回滚粒度粗，影响面大
  - 鉴权、异常、日志规范分散，难以规模化治理

### Value Proposition and Success Metrics
- **Value Proposition：** 清晰领域边界 + 统一底座能力 + 可观测可治理的微服务体系
- **Success Metrics：**
  - 关键链路（登录、发帖、点赞、搜索）在拆分后保持功能等价
  - 支持服务级独立部署/回滚
  - 可观测性：traceId 可贯穿 Gateway 与各服务日志

### Humanistic Care
迁移过程中需避免暴露敏感信息（邮箱、Token、密钥），并保持旧功能可用/可回滚，降低用户与维护者心智负担。

## Change Content

1. **迭代 0（打地基）：** 建新工程骨架（Boot 3 + Java 17 + Spring Cloud + Nacos），先把 `gateway + auth-service` 跑通；定义全局规范（API 返回、错误码、日志/traceId、鉴权方式、配置规范）。
2. **迭代 1（先拆旁路）：** 优先落地 `search-service`、`message-service`、`analytics-service`（事件驱动/读多写少，风险更低）。
3. **迭代 2（拆高频关系域）：** 落地 `social-service`（点赞/关注）独立伸缩，API 化供前端调用。
4. **迭代 3（拆核心域）：** 最后迁移 `content-service` 与 `user-service` 的核心读写，完成单体下线。

## Impact Scope

- **Modules：**
  - 新增：gateway/auth-service/user-service/content-service/social-service/message-service/search-service/analytics-service
  - 迁移：现单体内 controller/service/dao/config/interceptor/event/quartz 等逐步拆出
- **Files：**
  - `pom.xml` 将升级并可能演进为多模块父工程
  - 新增各服务 `pom.xml`、启动类、配置文件、API/DTO、测试与部署文件
  - 新增 `frontend/`（Vue3）
- **APIs：**
  - 新增 `/api/**` REST API；旧 `/index` 等页面接口进入兼容期并逐步下线
- **Data：**
  - 数据归属将逐步按服务拆分；迁移期可采用“共享库不同 schema/表归属”作为过渡
  - Kafka 事件协议需标准化并版本化

## Core Scenarios

### Requirement: 迭代 0 - 基础设施与规范
**Module:** infra
完成 Boot 3/Java 17 升级与微服务底座能力。

#### Scenario: Gateway + Auth 登录鉴权闭环
前置条件：本地基础设施可用（Nacos/MySQL/Redis/Kafka 等）
- Vue3 可登录并获取 token
- 受保护接口在 token 缺失/过期时返回 401
- 权限不足返回 403

### Requirement: 迭代 1 - 旁路服务拆分（search/message/analytics）
**Module:** search/message/analytics
把搜索、通知、统计从核心链路中解耦出来。

#### Scenario: 发帖后可被搜索命中并高亮
- content-service 发布 PostPublished 事件
- search-service 更新索引
- 搜索接口返回高亮字段

#### Scenario: 点赞/评论/关注后产生通知
- social/content 发布事件
- message-service 生成通知并可查询未读数

#### Scenario: UV/DAU 可查询
- gateway 或 analytics-service 记录 UV/DAU
- 区间查询返回正确统计值

### Requirement: 迭代 2 - 社交关系域（social-service）
**Module:** social
点赞/关注/粉丝独立服务化。

#### Scenario: 点赞/关注功能服务化后保持行为一致
- 点赞状态与计数正确
- 关注/粉丝列表正确（含时间排序）

### Requirement: 迭代 3 - 核心域拆分（content-service/user-service）
**Module:** content/user
把帖子/评论与用户资料拆成独立服务并完成单体下线。

#### Scenario: 发帖/评论/热帖链路保持功能等价
- 发布、评论、热帖排序可用
- 事件驱动索引/通知保持最终一致

#### Scenario: 用户资料/头像独立服务化
- 个人主页数据正确
- 头像上传与更新链路可用

## Risk Assessment
- **Risk：** Boot 3（Jakarta）迁移与依赖版本兼容（Security 6、ES/Kafka Client 等）成本高  
  **Mitigation：** 先锁定版本矩阵与 PoC；以模块化/分阶段迁移降低一次性改动面
- **Risk：** 服务拆分后数据一致性与调用链复杂度提升  
  **Mitigation：** 事件驱动 + 幂等消费 + 必要的读模型/聚合策略；优先拆旁路服务
- **Risk：** 前后端分离后鉴权与安全边界变化  
  **Mitigation：** JWT + Refresh Token 旋转；网关统一 CORS/限流/鉴权；敏感信息不落前端

