# 变更提案: module-deps-hardening

## 元信息
```yaml
类型: 重构/优化
方案类型: implementation
优先级: P0
状态: 草稿
创建: 2026-02-24
```

---

## 1. 需求

### 背景
当前仓库在“模块命名/依赖边界/启动期门禁”上存在 4 个高频争议点，长期会放大沟通成本与生产风险：

- **common（artifactId）与 platform（package）语义不一致**：`common/pom.xml` 的 `artifactId/name` 为 `common`，但主要代码位于 `common/src/main/java/com/nowcoder/community/platform/**`（例如 `platform/autoconfig`、`platform/startup` 等）。这会把“平台运行期能力”伪装成“随便放点共享代码”，导致边界治理依赖口头约定。
- **平台层出现按服务名分支的 prod 启动校验**：`common/src/main/java/com/nowcoder/community/platform/startup/StartupValidation.java` 在 prod 下读取 `spring.application.name`，并对 `auth-service` 做服务特有校验（SMTP、验证码、Cookie 等）。平台层开始理解业务细节，新增服务/新增校验会牵引 common 改动与发版。
- **Dubbo 扫描包过宽**：多个服务 `application.yml` 中 `dubbo.scan.base-packages` 统一为 `com.nowcoder.community`（例如 `content/content-service/src/main/resources/application.yml`），扫描范围包含依赖 jar 的同根包；未来一旦某个依赖误带 `@DubboService`，可能被意外暴露。
- **prod 配置导入 fail-fast 不一致**：多数服务已通过 `application-prod.yml` 把 `spring.config.import` 改为 required/fail-fast，但 `ops-service` 当前仅有 `application.yml`（缺少 `application-prod.yml`），存在 prod 配置缺失仍能启动的“静默退化”风险。

### 目标
- **边界清晰**：明确 `common` 的职责=platform-runtime（运行期平台层），避免被当作“万能工具箱”承载域语义。
- **可扩展**：启动期校验从“common 内按服务名分支”演进为“插件化/SPI”，新增服务校验不需要修改 common。
- **安全默认态**：Dubbo 扫描范围收敛到“服务自身根包”，减少误暴露与启动扫描成本；prod 配置导入策略在所有服务一致（至少包含 required 的 `${spring.application.name}.yaml`）。
- **可制度化**：用门禁测试（gate tests）把上述约束固化，避免回归靠约定。

### 约束条件
```yaml
时间约束: 允许分阶段落地（优先修复生产风险与边界穿透点）
性能约束: Dubbo 扫描收敛应降低（而非增加）启动成本
兼容性约束: 保持 dev/local 可不接 Nacos 启动的便捷性；prod 保持 fail-closed
业务约束: 不引入跨服务联动发版的新耦合点（新增校验应由服务自行演进）
```

### 验收标准
- [ ] `common` 不再包含任何按 `spring.application.name` 分支的服务特有启动校验逻辑（common 仅保留“通用校验 + 校验框架”）。
- [ ] 各服务可通过在自身模块声明 `StartupCheck`（或等价接口）Bean 注入启动校验，无需修改 common。
- [ ] 各服务 `dubbo.scan.base-packages` 收敛为“服务自身根包”（例如 `com.nowcoder.community.content`），并有门禁测试禁止回退到 `com.nowcoder.community`。
- [ ] `ops-service` 在 prod profile 下具备 required/fail-fast 的 Nacos 配置导入（与其它服务对齐），配置缺失时应启动失败而非带默认值运行。
- [ ] 文档明确：`common` 的语义=platform-runtime；并明确“common 不承载域语义、不按服务名分支”的硬约束。

---

## 2. 方案

### 技术方案
推荐采用 **方案 A（最小破坏演进）**，优先解决“边界穿透 + 生产静默退化 + 扫描过宽”的实质风险，并通过门禁测试制度化。

核心改造点：

1) **StartupValidation SPI/插件化**
- common 提供 `StartupValidationRunner` + `StartupCheck` 接口（或类似命名），Runner 在 `prod` profile 下收集并执行所有 `StartupCheck` Bean（支持 `@Order`）。
- common 仅保留“跨服务通用校验”（例如 JWT HMAC secret、trusted-proxy allowlist 等）。
- `auth-service` 的服务特有校验迁移到 `auth-service` 自身模块（以 Bean 的形式注册 `StartupCheck`），common 不再识别具体服务名。

2) **Dubbo 扫描收敛**
- 各服务将 `dubbo.scan.base-packages` 从 `com.nowcoder.community` 收敛到自身根包：`com.nowcoder.community.{service}`。
- 增加门禁测试：禁止 `dubbo.scan.base-packages=com.nowcoder.community`；可选白名单校验“必须等于该服务根包”。

3) **prod 配置导入对齐**
- 为 `ops-service` 补齐 `application-prod.yml`（或采用 `spring.config.activate.on-profile=prod` 的同文件块），确保 prod 下导入 `nacos:${spring.application.name}.yaml` 为 required/fail-fast。
- 增加门禁测试：每个受管理服务必须具备 prod 的 required 导入策略。

4) **common 语义硬约束（文档 + 门禁）**
- 文档明确：`common` = platform-runtime（运行期平台层），禁止承载域语义。
- 门禁测试补齐：防止 future 把服务特有逻辑塞回 common（例如检查 common 中是否出现按服务名分支的启动校验，或更通用地：common 不允许出现 `com.nowcoder.community.{service}` 包）。

### 影响范围
```yaml
涉及模块:
  - common: StartupValidation SPI 化 + 新增 gate tests
  - gateway/auth/user/content/social/message/search/analytics/ops: Dubbo scan base-packages 收敛；ops-service 补齐 prod 配置
  - docs: 模块语义与边界约束说明
预计变更文件: 15~30（取决于门禁策略与是否补充示例）
```

### 风险评估
| 风险 | 等级 | 应对 |
|------|------|------|
| SPI 化过程中遗漏现有校验（prod 行为漂移） | 高 | 先把现有校验拆分为“通用 check + auth 专属 check”，并新增单元测试覆盖缺失项；上线前以缺失配置验证 fail-closed 行为 |
| Dubbo 扫描收敛导致引用/Provider 未被扫描 | 中 | 逐服务修改并回归（本地启动/单测）；门禁测试保护，必要时显式补充 `@DubboComponentScan` 或调整包路径 |
| 门禁测试误报/过严影响开发效率 | 中 | 先用“禁止最坏值（com.nowcoder.community）”作为最低门槛，再逐步收紧到白名单；提供清晰报错指引 |
| 仅靠文档无法防止 common 膨胀 | 中 | 文档必须与 gate tests 配套；评审 checklist 也纳入“模块边界”条目 |

---

## 3. 技术设计（可选）

> 涉及架构变更、API设计、数据模型变更时填写

### 架构设计
```mermaid
flowchart TD
    App[Service ApplicationContext] --> Runner[StartupValidationRunner (common)]
    Runner --> Check1[CoreStartupCheck (common)]
    Runner --> Check2[AuthStartupCheck (auth-service)]
    Runner --> CheckN[Other StartupCheck (service-owned)]
```

### 接口草案（建议）
```java
public interface StartupCheck {
    void validateOrThrow(Environment environment);
}
```

Runner 负责：
- 仅在 `prod` profile 下启用（沿用现有 `@Profile("prod")`）
- 收集所有 `StartupCheck`（按 `@Order` 排序）
- 聚合错误并 `throw IllegalStateException`（保持 fail-closed）

---

---

## 4. 核心场景

> 执行完成后同步到对应模块文档

### 场景: prod 启动校验（通用 + 服务自有）
**模块**: common + service-owned
**条件**: `SPRING_PROFILES_ACTIVE=prod`
**行为**:
- Runner 执行 common 的通用校验（例如 JWT secret、trusted-proxy）
- Runner 执行服务自有校验（例如 auth-service 的 SMTP / 固定验证码禁用）
**结果**: 任一校验失败 → 启动被阻断（fail-closed），并输出缺失项清单与修复指引

---

## 5. 技术决策

> 本方案涉及的技术决策，归档后成为决策的唯一完整记录

### module-deps-hardening#D001: 选择“最小破坏演进（方案A）”作为近期主方案
**日期**: 2026-02-24
**状态**: ✅采纳
**背景**: 需要优先降低生产静默退化风险（ops-service prod 导入缺失）并修复平台层边界穿透（StartupValidation 按服务名分支），同时避免一次性大规模坐标迁移带来的发布牵引。
**选项分析**:
| 选项 | 优点 | 缺点 |
|------|------|------|
| A: 保留 common 坐标 + SPI 化 + 扫描收敛 + 门禁 | 解决核心风险点；分阶段落地；发布牵引最小；扩展性显著提升 | 命名歧义仍需文档/门禁约束；仍需后续评估是否拆分 |
| B: common 改名为 platform-runtime | 语义更清晰，沟通成本下降 | 坐标迁移牵引大（依赖、构建、镜像构建参数等）；短期 ROI 不如先修风险点 |
| C: common 拆分为 runtime + utils | 长期边界最清晰，依赖图更健康 | 一次性变更范围大，回归与迁移成本高 |
**决策**: 选择方案 A
**理由**:
- 直接命中已确认的高风险点：ops-service prod 导入缺失、StartupValidation 边界穿透、Dubbo 扫描过宽
- 以“门禁测试”制度化边界，避免回归靠约定
- 为未来演进预留空间：当 common 膨胀/依赖污染明显时，再评估向 C 拆分
**影响**: common + 全部服务配置（Dubbo/Nacos）+ 文档 + gate tests
