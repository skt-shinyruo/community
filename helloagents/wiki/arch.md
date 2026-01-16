# 架构设计

## 1. 当前总体架构（单体）

```mermaid
flowchart TD
    Browser[Browser] --> Monolith[Spring Boot 单体\nSpring MVC + Thymeleaf]
    Monolith --> MySQL[(MySQL)]
    Monolith --> Redis[(Redis)]
    Monolith --> Kafka[(Kafka)]
    Monolith --> ES[(Elasticsearch)]
    Monolith --> Qiniu[Qiniu 头像存储]
    Monolith --> Quartz[Quartz 定时任务]
```

---

## 2. 目标总体架构（Boot 3 + 微服务 + 前后端分离）

```mermaid
flowchart TD
    SPA[Vue3 SPA] --> GW[API Gateway]
    GW --> Auth[auth-service]
    GW --> User[user-service]
    GW --> Content[content-service]
    GW --> Social[social-service]
    GW --> Msg[message-service]
    GW --> Search[search-service]
    GW --> Ana[analytics-service]

    Auth --> Redis[(Redis)]
    Social --> Redis
    Ana --> Redis

    User --> MySQL[(MySQL)]
    Content --> MySQL
    Msg --> MySQL

    Content --> Kafka[(Kafka)]
    Social --> Kafka
    Auth --> Kafka

    Search --> ES[(Elasticsearch)]
    User --> Qiniu[Qiniu]

    GW -. service discovery/config .-> Nacos[(Nacos)]
    Auth -.-> Nacos
    User -.-> Nacos
    Content -.-> Nacos
    Social -.-> Nacos
    Msg -.-> Nacos
    Search -.-> Nacos
    Ana -.-> Nacos
```

---

## 3. 技术栈
- **Backend：** Java 17 / Spring Boot 3.x / Spring Cloud / Spring Cloud Alibaba Nacos
- **Frontend：** Vue 3
- **Data：** MySQL / Redis / Kafka / Elasticsearch / Qiniu

---

## 4. 核心流程示例（目标态）

```mermaid
sequenceDiagram
    participant U as 用户(浏览器)
    participant FE as Vue3
    participant GW as Gateway
    participant AU as auth-service
    participant CT as content-service
    participant KC as Kafka
    participant MS as message-service

    U->>FE: 登录提交
    FE->>GW: POST /api/auth/login
    GW->>AU: 转发鉴权请求
    AU-->>GW: 返回 access token / refresh token
    GW-->>FE: 返回 token

    U->>FE: 发布帖子
    FE->>GW: POST /api/posts (Authorization: Bearer)
    GW->>CT: 转发请求
    CT-->>KC: 发布 PostPublished 事件
    KC-->>MS: 消费事件并生成通知
    CT-->>GW: 发布成功
    GW-->>FE: 发布成功
```

---

## 5. 重大架构决策（ADR 索引）

| adr_id | title | date | status | affected_modules | details |
|--------|-------|------|--------|------------------|---------|
| ADR-001 | Boot 3 + Java 17 + Nacos 微服务底座 | 2026-01-16 | ✅Adopted | gateway/auth/user/content/social/message/search/analytics | [Link](../plan/202601161428_boot3_ms_vue3_nacos/how.md#adr-001-boot-3--java-17--nacos-微服务底座) |

