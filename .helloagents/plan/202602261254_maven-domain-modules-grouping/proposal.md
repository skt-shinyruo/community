# 变更提案: maven-domain-modules-grouping

## 元信息
```yaml
类型: 工程结构重构
方案类型: implementation
优先级: P1
状态: ✅已完成
创建: 2026-02-26
完成: 2026-02-26
```

---

## 1. 需求

### 背景
当前根目录下的 Maven 模块以“横向能力 + 领域模块”混排，且 5 个业务域（analytics/content/search/social/user）各自的 `*-api` / `*-service` 模块直接挂在 root `pom.xml` 的 `<modules>` 下：

- 目录结构在阅读与维护上成本较高：同一域的 API/服务模块分散在根目录，与其它基础设施模块（`common`、`infra-*`、`contracts-*` 等）混在一起。
- 多处存在“按模块目录路径硬编码”的引用：文档中直接写 `content-service/src/test/...`、`content-api/src/...` 等，迁移目录后会失效。
- 镜像构建脚本 `deploy/Dockerfile.spring-service` 使用 build arg `MODULE` 既作为 `mvn -pl` 选择器又作为 `cp "${MODULE}/target/*.jar"` 的目录路径，目录迁移会直接破坏镜像构建（即使 Maven `-pl` 能按坐标选择模块，`cp` 仍会失败）。

因此需要一次结构化迁移：把这 5 个域的成对模块迁移到 `/{domain}/` 下，并通过域级聚合 POM 统一承接 parent + 聚合职责，提升可维护性并降低“路径耦合”风险。

### 目标
- **模块按域归组**：将 root `pom.xml` 当前 `<modules>` 中 5 个域（analytics/content/search/social/user）的 `*-api` / `*-service` 成对模块迁移到 `/{domain}/` 目录下。
- **新增域级聚合/父 POM**：为每个域新增 `/{domain}/pom.xml`（`packaging=pom`）作为共同 parent + 聚合器（aggregator）。
- **子模块 parent 变更**：迁移后的 `*-api` / `*-service` 模块 `pom.xml` 的 `<parent>` 改为对应域 `pom.xml`；root `pom.xml` 的 `<modules>` 改为引用这些域聚合模块。
- **坐标不变**：所有 leaf（`*-api` / `*-service`）模块的 `groupId/artifactId/version` 保持不变，避免影响依赖关系与发布坐标。
- **构建通过**：最终 `./mvnw -q test`（仓库根目录）通过。

### 约束条件
```yaml
时间约束: 允许一次性落地（但需分步骤验证，先 validate 再 full test）
性能约束: 构建不应显著变慢；Docker 构建仍需保持依赖缓存复用策略
兼容性约束: leaf 模块 GAV 不变；不改变现有服务启动/运行时配置语义
工程约束: 仅迁移 analytics/content/search/social/user 的 api/service；不扩大到其它模块（如 auth/message/ops 等）
运维约束: `deploy/docker-compose.yml` 中现有 build args（`MODULE: xxx-service`）尽量不需要因目录迁移而改动
```

### 验收标准
- [√] 目录迁移完成，且路径映射符合约定：
  - `analytics-api` → `analytics/analytics-api`；`analytics-service` → `analytics/analytics-service`
  - `content-api` → `content/content-api`；`content-service` → `content/content-service`
  - `search-api` → `search/search-api`；`search-service` → `search/search-service`
  - `social-api` → `social/social-api`；`social-service` → `social/social-service`
  - `user-api` → `user/user-api`；`user-service` → `user/user-service`
- [√] 新增 5 个域聚合模块：`analytics/pom.xml`、`content/pom.xml`、`search/pom.xml`、`social/pom.xml`、`user/pom.xml`，均为 `packaging=pom`，同时承担 parent + aggregator。
- [√] 各 `*-api` / `*-service` 的 `pom.xml`：
  - `groupId/artifactId/version` 不变
  - `<parent>` 改为对应域聚合模块（例如 `com.nowcoder.community:content`）
- [√] root `pom.xml` 的 `<modules>`：
  - 不再直接引用这 10 个 leaf 模块目录
  - 改为引用 5 个域聚合模块（`analytics/content/search/social/user`）
- [√] 文档与部署脚本中不再引用旧路径（至少修复已发现的硬编码证据）：
  - `docs/SYSTEM_DESIGN.md` 中 `content-service/src/test/...`
  - `docs/DATA_MODEL.md` 中 `content-api/src/...`、`social-api/src/...`
  - `deploy/Dockerfile.spring-service` 不再把 `MODULE` 当作目录路径（迁移后可继续使用 `MODULE=content-service` 等）
- [√] `./mvnw -q test` 通过（若存在依赖 Docker 的 Testcontainers 测试，需明确其在 CI/本地的策略，不得导致默认 `mvnw test` 不稳定）

### 执行结论（落地结果）
- Maven 结构与目录结构已对齐为“按域归组 + 域聚合 parent/aggregator”。
- `deploy/Dockerfile.spring-service` 已按 `artifactId` 构建与拷贝产物，compose 中 `MODULE: xxx-service` 无需跟随目录迁移调整。
- 镜像构建冒烟验证在本次环境中遇到 Docker Hub 网络超时（`TLS handshake timeout`）未能稳定复现；建议在网络通畅环境下执行一次：`docker build --build-arg MODULE=content-service -f deploy/Dockerfile.spring-service .`。

---

## 2. 方案

### 技术方案
核心思路：引入“域级聚合 POM”这一层，把同一域的 API/服务模块聚合起来，并让 leaf 模块的 parent 从 root 切换为域 parent；root 仅聚合域级模块，从而实现目录归组与依赖继承关系的对齐。

迁移步骤（建议按域逐个落地，逐步验证）：

1) **为每个域新增聚合/父 POM：`/{domain}/pom.xml`**
- `packaging=pom`
- `<parent>` 指向 root：`com.nowcoder.community:community:${project.version}`
- `<artifactId>` 建议为 `{domain}`（例如 `content`），便于在人类语义与目录结构上对齐
- `<modules>` 仅包含该域的两个子模块（例如 `content-api`、`content-service`）

2) **迁移目录结构**
- 将 `{domain}-api` 与 `{domain}-service` 目录移动到 `/{domain}/` 下（保留模块目录名不变，仅新增一层域目录）

3) **更新 leaf 模块 `pom.xml` 的 parent**
- 将 `<parent>` 从 `community` 改为域聚合模块（例如 `content`）
- 由于 leaf 模块新的相对路径为 `/{domain}/{module}/pom.xml`，默认 `../pom.xml` 将自然指向域 parent，无需额外设置 `<relativePath>`
- 保持 leaf 的 `artifactId` 不变（例如仍为 `content-service`），避免影响依赖坐标

4) **更新 root `pom.xml` 的 `<modules>`**
- 移除对这 10 个 leaf 模块的 `<module>` 条目
- 新增对 5 个域聚合模块的 `<module>` 条目（`analytics`、`content`、`search`、`social`、`user`）

5) **Docker 构建去“路径耦合”**
- 调整 `deploy/Dockerfile.spring-service`：`ARG MODULE` 语义改为 **Maven `artifactId`**（而非目录路径）
  - 构建使用 `mvn -pl ":${MODULE}" -am package`（按坐标选择模块）
  - 产物拷贝通过在工作区中按 jar 名称模式查找（`*/target/${MODULE}-*.jar`，排除 `*.original`）获取 jar 路径，避免依赖模块目录层级
- 确保 `deploy/docker-compose.yml` 中现有 `MODULE: content-service` 等无需改动（仍然传 artifactId）

6) **文档/脚本引用修复**
- 修复已发现的硬编码路径，并进行一次全仓扫描，补齐其它潜在引用（尤其是 `docs/`、`deploy/`、`scripts/`）

7) **验证**
- 先执行 `./mvnw -q -DskipTests validate` 快速验证 reactor / POM 结构
- 再执行 `./mvnw -q test` 作为最终验收
- 若涉及 Testcontainers：以 `./mvnw -q test` 为准，必要时将 Docker 依赖测试做 profile/条件控制，避免默认测试不稳定

### 影响范围
```yaml
涉及模块:
  - pom.xml(根): <modules> 引用从 leaf 改为域聚合模块
  - analytics/content/search/social/user: 新增域级聚合 pom + 目录归组
  - analytics-api/content-api/search-api/social-api/user-api: 迁移目录 + parent 指向域聚合 pom（GAV 不变）
  - analytics-service/content-service/search-service/social-service/user-service: 迁移目录 + parent 指向域聚合 pom（GAV 不变）
  - deploy: Dockerfile 构建参数语义调整（去路径耦合），必要时补充 README
  - docs: 更新硬编码路径引用
预计变更文件: 25~50（以全仓路径引用扫描命中数量为准）
```

### 风险评估
| 风险 | 等级 | 应对 |
|------|------|------|
| Maven reactor 结构调整导致构建失败（modules 路径/parent 解析错误） | 高 | 按域逐个迁移；每次迁移后先 `./mvnw -q -DskipTests validate`；确保域 POM 的 `<modules>` 指向正确相对路径 |
| Docker 构建因 `MODULE` 目录路径变化而失败 | 高 | 将 Dockerfile 的 `MODULE` 定义为 artifactId，并按 jar 名称模式复制产物；保持 `deploy/docker-compose.yml` 的 `MODULE` 不变 |
| 文档/脚本出现更多旧路径硬编码，迁移后失效 | 中 | 执行全仓 `rg` 扫描旧路径前缀（`content-service/` 等），修复后再跑 `./mvnw -q test` 与（可选）docker build smoke test |
| leaf 坐标意外变化（影响依赖关系） | 中 | 明确约束：只改 parent 与目录层级，不改 `artifactId/version/groupId`；迁移后抽样核对 `mvn -q -Dexec...`（或检查 POM） |
| Testcontainers 测试需要 Docker，导致 `./mvnw test` 在无 Docker 环境不稳定 | 中 | 以 `./mvnw -q test` 为验收门槛：若 CI 无 Docker，则将容器相关测试改为条件执行/profile；若 CI 有 Docker，则在文档中明确要求与失败指引 |
| IDE/开发体验短期波动（模块路径变化） | 低 | 迁移后更新 README/文档说明新目录结构；必要时给出 IntelliJ 重新导入项目指引 |

---

## 3. 技术设计（可选）

> 涉及架构变更、API设计、数据模型变更时填写

### 架构设计
```mermaid
flowchart TD
    Root[community (root pom)] --> A[analytics (pom)]
    Root --> C[content (pom)]
    Root --> S[search (pom)]
    Root --> SO[social (pom)]
    Root --> U[user (pom)]

    A --> A1[analytics-api (jar)]
    A --> A2[analytics-service (jar)]

    C --> C1[content-api (jar)]
    C --> C2[content-service (jar)]

    S --> S1[search-api (jar)]
    S --> S2[search-service (jar)]

    SO --> SO1[social-api (jar)]
    SO --> SO2[social-service (jar)]

    U --> U1[user-api (jar)]
    U --> U2[user-service (jar)]
```

---

## 4. 核心场景

> 执行完成后同步到对应模块文档

### 场景: 以域为单位维护与演进模块
**模块**: `{domain}/` + `/{domain}/pom.xml`
**条件**: 需要新增/修改某一业务域的 API/Service 模块
**行为**:
- 开发者进入 `/{domain}/` 目录即可看到该域聚合模块及其子模块
- 该域的 `*-api` 与 `*-service` 共享同一个域 parent（依赖版本/插件继承一致）
**结果**: 目录结构与 Maven 继承结构一致，降低维护成本

---

## 5. 技术决策

> 本方案涉及的技术决策，归档后成为决策的唯一完整记录

### maven-domain-modules-grouping#D001: 选择“域聚合迁移 + Docker 去路径耦合（方案A）”作为本次落地主线
**日期**: 2026-02-26
**状态**: ✅采纳
**背景**: 需要在不改变 leaf 模块坐标（GAV）的前提下完成目录归组；同时已确认存在 Dockerfile/文档对旧路径的硬编码，必须在迁移中一并消除，否则会出现“结构迁移完成但交付链路不可用”的问题。
**选项分析**:
| 选项 | 优点 | 缺点 |
|------|------|------|
| A: 域聚合迁移 + Docker 去耦 | 目录结构与领域语义一致；leaf 坐标不变、改动可控；root reactor 更清晰；通过按 `artifactId` 构建/拷贝产物，镜像构建对目录层级不敏感 | 引入 5 个新的聚合 POM（artifactId=domain）；短期会涉及一次全仓路径引用修复 |
| B: 引入 build-parent / 大规模重构（更激进） | 可进一步统一 plugin/dependency 管理，减少重复配置；长期可为更彻底的分层/模块化治理打基础 | 变更范围大（可能涉及更多模块 parent 体系、CI/发布/镜像构建参数）；一次性风险高，回归成本大，不符合“只迁移 5 域”边界 |
**决策**: 选择方案 A
**理由**:
- 与本次明确目标严格对齐：只迁移 5 个域的 api/service 并新增域聚合 POM，不扩大范围
- leaf 模块坐标不变，降低跨模块依赖风险
- 同步解决已发现的交付链路风险：Dockerfile/文档的路径耦合
**影响**: `pom.xml`（root + 5 个域）+ 10 个 leaf 模块目录与 parent + `deploy/` + `docs/`
