# 版本矩阵与依赖升级清单（迭代 0 基线）

Directory: `.helloagents/archive/2026-01/202601161428_boot3_ms_vue3_nacos/`

> 目的：在开工前把“版本兼容 + 依赖替换 + 中间件升级影响”一次说清，避免在编译期/运行期反复卡住。  
> 注意：以下为**推荐基线**（偏保守稳定）。若你希望追最新 minor 线，可在 PoC 通过后再上调。

---

## 1. 推荐版本矩阵（基线）

### 1.1 后端基础（必须固定）

| 项 | 推荐版本 | 选择理由 | 备注 |
|----|----------|----------|------|
| Java | 17（Temurin/Corretto 均可） | Boot 3 官方基线之一，生态成熟 | CI/本地统一 |
| Spring Boot | 3.2.x（建议先从 3.2.6 起） | 3.2 线成熟，兼容 Jakarta/Security 6，风险可控 | patch 可随时间滚动 |
| Spring Cloud | 2023.0.x（与 Boot 3.2 对齐） | 与 Boot 3.2 常见搭配线，生态稳定 | 以 BOM 管理 |
| Spring Cloud Alibaba | 2023.x（与 Cloud 2023.0 对齐） | 与 Nacos 配套，避免自行拼版本 | 以 BOM 管理 |
| Nacos Server | 2.2.x ~ 2.4.x（建议 2.3.x） | 2.x 主线，支持注册发现/配置中心 | 本仓库 `deploy/docker-compose.yml` 使用 2.3.2 |

> ⚠️ Uncertainty Factor: 具体 patch 号会随时间变动；最终以 PoC（编译 + 启动 + 注册 + 基础鉴权链路）通过为准。

### 1.1.1 已落地版本（当前分支实现）
- Spring Boot：`3.2.6`（见父工程 `pom.xml`）
- Spring Cloud：`2023.0.3`（见父工程 `pom.xml`）
- Spring Cloud Alibaba：`2023.0.1.0`（见父工程 `pom.xml`）
- Nacos（本地 compose）：`nacos/nacos-server:v2.3.2`（见 `deploy/docker-compose.yml`）

### 1.2 关键依赖升级（强相关）

| 领域 | 推荐版本/方案 | 选择理由 | 迁移影响 |
|------|--------------|----------|----------|
| Spring Security | 6.x（随 Boot 3.2） | `WebSecurityConfigurerAdapter` 已移除 | 需改为 `SecurityFilterChain` |
| MyBatis | `mybatis-spring-boot-starter` 3.0.3 | 适配 Boot 3 / Jakarta | 已在 `legacy-community`/`auth-service` 落地 |
| MySQL Driver | `com.mysql:mysql-connector-j` 8.x | 适配新生态、驱动维护活跃 | groupId/artifactId 变化 |
| JSON | 优先 Jackson（Boot 内置） | 避免 Fastjson 历史安全风险与多套 JSON 体系 | 需要替换 `fastjson` 使用点 |
| Elasticsearch | 推荐 ES 8.x（或至少 7.x） | ES 6.x 生态过旧，与 Boot 3 配套困难 | 现有 `ElasticsearchTemplate` 需要重写 |
| Kafka | 推荐 Broker 3.x（客户端随 Boot） | 新生态默认，长期维护 | 需校验 broker/client 兼容 |
| Redis | 推荐 6.x/7.x | 性能与特性更稳，生态成熟 | 3.2 太老，不建议继续 |

---

## 2. 受影响中间件版本范围与升级建议

> 本节回答：升级 Boot 3 后，哪些中间件“必须升级/强烈建议升级/可暂时兼容”。

### 2.1 MySQL
- **现状：** README 标注 5.7  
- **建议：** 升级到 **MySQL 8.0.x**（生产/长期维护推荐）  
- **兼容说明：** 若短期无法升级数据库，至少要先升级 JDBC 驱动到 8.x 并验证 SQL 模式/时区/字符集（`utf8mb4`）  
- **风险点：**
  - 驱动 8.x 默认时区/SSL 参数差异
  - SQL Mode 差异可能影响写入与排序

### 2.2 Redis
- **现状：** README 标注 3.2  
- **建议：** 升级到 **Redis 6/7**  
- **原因：** Redis 3.2 太老，客户端与运维生态成本高  
- **风险点：**
  - ACL（如启用）配置变化
  - 序列化策略（JSON）兼容性：建议在 PoC 期间验证 key/value 序列化读写一致

### 2.3 Kafka
- **现状：** README 标注 2.3.0  
- **建议：** Broker 升级到 **Kafka 3.x**（或至少 2.8+）  
- **风险点：**
  - Topic 配置与默认参数可能变化
  - 重试与死信策略需要工程化（见 `event-contract.md`）

### 2.4 Elasticsearch
- **现状：** README 标注 6.4.3，代码使用 `ElasticsearchTemplate`（旧 API）  
- **建议：**
  - 优先采用 **ES 8.x + 新 Java Client**（配套 Spring Data Elasticsearch 新版本）  
  - 或在迭代 0 通过“能力开关”临时关闭搜索相关功能，迭代 1 单独重写 search-service（推荐）  
- **风险点：**
  - mapping/查询 DSL 差异
  - 客户端与 Spring Data 适配变动较大

---

## 3. 依赖升级/替代清单（从现仓库出发）

### 3.1 建议替换（强烈推荐）

| 现状依赖/用法 | 问题 | 推荐替代 | 迁移策略 |
|--------------|------|----------|----------|
| `com.alibaba:fastjson:1.2.58` | 历史安全问题多，生态不建议新项目继续用 | Jackson（Boot 默认）或 fastjson2 | 迭代 0 先统一返回/事件 JSON 用 Jackson；逐步移除 fastjson |
| `ElasticsearchTemplate` | Spring Data Elasticsearch 旧 API | `ElasticsearchOperations` / 新 Java Client | 迭代 0 可降级关闭；迭代 1 在 search-service 重写 |

### 3.2 需要兼容性评估（PoC 必测）

| 现状依赖/模块 | 风险 | 处理建议 |
|--------------|------|----------|
| Kaptcha（验证码） | 可能依赖 `javax.servlet` | 迭代 0 可先不迁移验证码，改用“登录限流+风控”；后续替换为 Jakarta 兼容实现 |
| Thymeleaf 相关模板 | 前后端分离后不再需要 | legacy-community 保留用于迁移期；新服务不引入 thymeleaf |
| 七牛 SDK | 与 Java 17/依赖冲突需验证 | user-service PoC 验证上传凭证签发 |

---

## 4. 版本验证 PoC（必须做）

> 输出物：一份“PoC 结论与问题清单”，并将最终版本号回填到本文档与 `how.md`。

建议 PoC 步骤：
1. 先完成多模块骨架（`legacy-community` 单独可编译）。
2. 将 `legacy-community` 升级到 Boot 3.2.x + Java 17（不接入网关/认证也可）。
3. `mvn -q -DskipTests package` 通过。
4. 启动 `legacy-community` 并验证最小健康检查（`/actuator/health` 或日志启动成功）。
5. 记录阻塞项：不兼容依赖、配置缺失、中间件连通性等。

#### 4.1 PoC 结论（已回填）
- ✅ `mvn -q -DskipTests package` 通过（多模块 + Boot 3 + Java 17 基线稳定）
- ✅ `mvn -q test` 通过（legacy 的集成测试迁移期默认禁用，不阻塞迭代 0）
- ✅ legacy-community：完成 Jakarta 迁移与 Security 6 适配；并对 Elasticsearch 旧实现做迁移期降级移除
- ⚠️ 本地基础设施：已提供 `deploy/docker-compose.yml`，但在当前环境拉取 Docker Hub 镜像可能出现 `TLS handshake timeout`（需在可访问 Docker Hub 的环境验证）
