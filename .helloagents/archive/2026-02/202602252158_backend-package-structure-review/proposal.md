# 变更提案: backend-package-structure-review

## 元信息
```yaml
类型: 优化（结构评审）
方案类型: overview
优先级: P2
状态: ✅完成
创建: 2026-02-25
```

---

## 1. 需求

### 背景
- 本项目为 Maven 多模块微服务工程（Spring Boot 3 + Java 17），包含 `gateway` 与多业务服务，并通过 `contracts-*`/`*-api` 建立跨服务契约层。
- 随着演进，模块/包结构容易出现漂移（如：service 互相直接依赖、契约层引入 runtime 细节、common 变成“上帝模块”），需要定期评审来提前发现与收敛风险。

### 目标
- 输出“后端包/模块结构合理性评审报告”：回答“是否合理、为什么、哪里有风险、怎么改更好（仅建议不落地）”。
- 给出可执行的改进建议（按优先级），并形成可追溯归档记录（供后续实施时引用）。

### 约束条件
```yaml
范围约束: 仅评审后端 Java（Maven modules + Java package），不覆盖 frontend/deploy/loadtest。
操作约束: 只读评审，不修改业务代码/目录结构/构建配置。
证据约束: 关键结论需能在 pom.xml/目录结构/代码搜索中找到依据。
```

### 验收标准
- [x] 给出明确总体结论（合理/不合理 + 依据）
- [x] 列出关键优点、主要风险点与改进建议（含优先级）
- [x] 关键结论尽量附带证据（文件路径/模块名/关键依赖）
- [x] 评审报告与决策记录已归档（对应 archive 路径可追溯）

---

## 2. 方案

### 技术方案（评审方法）
1. 阅读根聚合 `pom.xml` 的 `<modules>` 与 `dependencyManagement`，确认模块层次与版本治理策略。
2. 抽查 `contracts-*`、`infra-*`、`common`、`*-api`、`*-service` 的 `pom.xml`：
   - 关注依赖方向是否符合“service → api → contracts”的单向依赖。
   - 关注 `*-api` 是否保持“纯接口/DTO”（不引入 Spring、不包含实现类）。
3. 扫描全仓 `*/pom.xml`，确认不存在 `com.nowcoder.community:* -service` 被其他模块作为 `<dependency>` 引入（避免实现级耦合与循环依赖）。
4. 抽查典型业务服务的 Java 包结构，确认包分层是否稳定（`api/config/dao/domain/outbox/rpc/service` 等）与职责是否清晰。

### 评审结论（摘要）
- **总体结论：合理。** 当前后端模块结构是一个清晰的“微服务 + 契约层”工程化分层：`contracts-*` 足够纯、`*-api` 足够轻、`*-service` 只依赖 `*-api` 不互相依赖，整体耦合风险可控。
- **需要收敛的点：** 存在少量不一致/潜在风险（如 `message-api/` 残留目录、`common` 边界容易膨胀、KB 模块索引粒度与实际模块不一致、Dubbo 依赖引入方式可能重复）。

### 证据与发现点

#### ✅ 优点（合理性证据）
- **模块分层清晰：** 根 `pom.xml` 明确聚合 `contracts-*`、`infra-*`、`common`、`*-api`、`*-service`（见 `pom.xml` 的 `<modules>`）。
- **契约层纯净：**
  - `contracts-core` 运行期仅依赖 `slf4j-api`（见 `contracts-core/pom.xml`），无 Spring 运行期依赖。
  - `contracts-event-core` 只引入 Jackson 序列化相关依赖（见 `contracts-event-core/pom.xml`）。
- **RPC API 模块足够轻：** `*-api` 模块统一只依赖 `contracts-core`（示例：`user-api/pom.xml`、`content-api/pom.xml`、`search-api/pom.xml`），未引入 Spring 相关依赖。
- **依赖方向健康：** 业务服务通过 `*-api` 协作而非直接依赖其他 `*-service`（示例：`user-service/pom.xml` 依赖 `user-api/social-api/content-api`）。
- **实现级耦合约束落地：** 未发现任一模块在 `<dependency>` 中直接依赖 `com.nowcoder.community:* -service`（本次评审通过扫描 `*/pom.xml` 验证）。

#### ⚠️ 风险 / 不一致点
- **目录级悬挂模块：** 存在 `message-api/` 目录，但无 `pom.xml` 且 `src/` 为空，易造成误导（开发者以为存在 message 的 RPC API 模块）。
- **模块文档粒度偏粗：** `.helloagents/modules/_index.md` 以“业务服务”维度为主，未显式列出 `contracts-*`、`infra-*`、`*-api` 等细粒度模块，影响导航与边界认知（尤其是新人/跨团队协作）。
- **common 变胖风险：** `common/pom.xml` 以 `provided` scope 暴露了 web/webflux/security/redis/kafka/jdbc 等多种能力，短期利于复用，但长期容易承载业务逻辑/领域模型，形成“上帝模块”并制造隐性耦合。
- **Dubbo 依赖引入方式可能重复：** 多数 service pom 同时声明 `infra-dubbo-starter` 与 `dubbo-spring-boot-starter`/`dubbo-registry-nacos`（示例：`user-service/pom.xml`、`gateway/pom.xml`），需持续关注版本漂移与依赖重复的维护成本（建议明确 infra-starter 的定位与职责）。

#### ✅ 建议（按优先级）
- **P0（低风险、建议尽快收敛）**
  - 清理 `message-api/` 残留目录：若无需独立 RPC API 模块，建议删除；若需要，则补齐 `pom.xml` 并纳入根 `pom.xml` 的 `<modules>`（同时将实际 RPC 接口/DTO 放入该模块）。
  - 更新知识库模块索引（`.helloagents/modules/_index.md`）：补充 `contracts-*`、`infra-*`、`*-api` 的职责与依赖方向说明，减少口径漂移。
- **P1（持续治理，防止结构回潮）**
  - 对 `common` 建立“边界守卫”：明确哪些内容允许进入 `common`（Result/错误码/trace/少量通用组件），禁止业务域逻辑进入；必要时通过架构测试/依赖门禁做约束。
  - 统一 Dubbo 依赖引入策略：明确 `infra-dubbo-starter` 是“仅 filters（provided）”还是“统一 starter（传递引入 dubbo）”，避免重复声明与长期漂移。
- **P2（可选的结构演进方向，需权衡成本/收益）**
  - 若模块数继续增长导致维护成本上升，可考虑将 `*-api` 进一步按域合并（例如 `contracts-rpc-*`），但需严格评估耦合/发布节奏与依赖稳定性。

### 影响范围
```yaml
涉及模块:
  - pom.xml: 模块聚合与依赖管理（只读）
  - contracts-*/infra-*/common/*-api/*-service: pom 与包结构抽查（只读）
预计变更文件: 0（本次仅评审，不落地改动）
```

### 风险评估
| 风险 | 等级 | 应对 |
|------|------|------|
| 抽查导致遗漏边界问题 | 中 | 若后续要落地重构，建议补充一次“全量依赖图 + 关键包职责审计” |
| 结构治理口径漂移（文档与代码不一致） | 中 | 以代码为准，补齐 KB 模块索引与边界说明；新增/强化门禁防回潮 |
| `common` 继续膨胀造成隐性耦合 | 中 | 建立 common 准入清单与依赖约束；必要时拆分为更细颗粒模块 |

---

## 3. 技术设计（可选）

> 涉及架构变更、API设计、数据模型变更时填写

### 架构设计
本次为只读评审报告，不做架构改动设计。

### API设计
本次为只读评审报告，不做 API 改动设计。

### 数据模型
本次为只读评审报告，不做数据模型改动设计。

---

## 4. 核心场景

> 执行完成后同步到对应模块文档

本次为只读评审报告，无新增/变更场景。

---

## 5. 技术决策

> 本方案涉及的技术决策，归档后成为决策的唯一完整记录

### backend-package-structure-review#D001: 包/模块分层维持现状，小步收敛不一致点
**日期**: 2026-02-25
**状态**: ✅采纳
**背景**: 当前结构整体健康且与历史 ADR 方向一致（contracts 纯化、service 禁止互依赖、RPC 通过 `*-api` 协作）。相比“大重构”，小步修复不一致点（如残留目录、文档粒度、common 边界）风险更低、收益更确定。
**选项分析**:
| 选项 | 优点 | 缺点 |
|------|------|------|
| A: 保持现有分层，仅修复不一致点（推荐） | 低风险、与现有 ADR 对齐、可以快速收敛杂音 | 需要持续治理，无法一次性“结构完美” |
| B: 合并 `*-api` 到更少的契约模块 | 模块数减少、管理更集中 | 契约体积变大、跨域耦合上升、发布节奏更难协调 |
| C: 每服务拆分 domain/application/adapter 多子模块 | 更强的代码层内边界、可测性更强 | 模块数量激增、改造成本高、短期收益不确定 |
**决策**: 选择方案 A
**理由**: 当前最主要问题是“少量不一致点/边界治理”，不是“架构失控”。A 能在不打断迭代节奏的前提下持续降低结构风险。
**影响**: `.helloagents` 文档补齐与（可选）构建/门禁规则增强；业务代码本次不改动。
