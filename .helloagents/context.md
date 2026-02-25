# 项目上下文

## 1. 基本信息

```yaml
名称: community
描述: 讨论社区微服务工程（Spring Boot 3 + Java 17 + Vue3）
类型: Web 应用（微服务 + SPA）
状态: 开发中
```

## 2. 技术上下文

```yaml
后端语言: Java 17
后端框架: Spring Boot 3.2.6 + Spring Security 6 + Spring Cloud 2023.0.x
前端: Vue 3（Vite）
服务治理: Nacos（注册发现/配置中心）+ Dubbo（服务间同步调用）
持久化: MyBatis + MySQL 8.0
缓存: Redis 7
消息: Kafka
搜索: Elasticsearch
构建: Maven（后端）+ npm（前端）
```

### 主要依赖（概要）

| 依赖 | 版本/约定 | 用途 |
|------|-----------|------|
| Java | 17 | 运行时/编译目标 |
| Spring Boot | 3.2.6 | 服务框架 |
| Spring Cloud | 2023.0.x | 网关与服务治理 |
| Spring Security | 6 | 鉴权与授权 |
| Dubbo | (见配置) | 服务间同步 RPC |
| Nacos | 2.3.2（本地 compose） | 注册发现/配置中心 |
| MySQL | 8.0（本地 compose） | 主存储 |
| Redis | 7（本地 compose） | 缓存与部分状态 |
| Kafka/Zookeeper | 7.6.1（compose） | 事件驱动与投影 |
| Elasticsearch | 8.12.2（compose） | 搜索 |

## 3. 项目概述

### 核心功能
- 账号体系：注册/激活/登录/刷新/登出、验证码、找回密码
- 内容域：发帖、评论/回复、敏感词过滤、热帖分数刷新
- 社交域：点赞、关注、拉黑
- 消息域：私信、系统通知
- 搜索与统计：ES 搜索、UV/DAU
- 运维平面：`/api/ops/**`（高风险/高成本操作隔离）

### 项目边界

```yaml
范围内:
  - 面向社区讨论场景的核心能力（账号/内容/社交/消息/搜索/统计）
  - 可本地一键拉起（docker compose）并进行最小链路联调
范围外:
  - 支付、电商、金融交易
  - 多租户/复杂 RBAC 权限模型（当前仅基础角色与运维隔离）
```

## 4. 开发约定

### 4.1 文档与 SSOT
- 工程与微服务边界、运维入口与安全策略，以 `.helloagents/project.md` 与仓库 `docs/` 为准。
- 代码是运行时行为的唯一客观事实；文档与代码不一致时以代码为准，并同步更新文档。

### 4.2 错误处理
```yaml
对外返回: Result{code,message,data,traceId,timestamp}
错误码: 按模块分段（例如 AUTH_**** / USER_**** / CONTENT_****）
异常收敛: RestControllerAdvice 统一处理；敏感堆栈仅写日志
```

### 4.3 测试策略（Unit-only）
```yaml
默认回归: mvn test 仅单元测试
禁止项: @SpringBootTest / 切片测试 / Testcontainers / 真实网络 IO
建议: 直接 new Controller/Service + Mockito + in-memory stub
```

## 5. 当前约束（源自历史决策）

> 这些是当前生效的技术约束，详细决策过程见对应归档记录。

| 约束 | 原因 | 决策来源 |
|------|------|---------|
| 默认仅保留 Unit Tests（CI/mvn test 不启动 Spring 容器） | 保证回归稳定、无外部依赖 | [tests_unit_only](archive/2026-02/202602221539_tests_unit_only/why.md) |
| 运维入口统一收敛到 `/api/ops/**` | 降低攻击面与漂移风险 | [internal_ops_dubbo_unification](archive/2026-02/202602132020_internal_ops_dubbo_unification/why.md) |
| outbox-only 为默认安全态（可靠投递） | 避免语义分叉与隐性降级 | [architecture_deep_refactor](archive/2026-02/202602241115_architecture_deep_refactor/why.md) |

## 6. 已知技术债务（可选）

| 债务描述 | 优先级 | 来源 | 建议处理时机 |
|---------|--------|------|-------------|
| （待补充） | P2 | - | - |
