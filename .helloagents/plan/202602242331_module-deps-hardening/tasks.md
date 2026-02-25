# 任务清单: module-deps-hardening

```yaml
@feature: module-deps-hardening
@created: 2026-02-24
@status: pending
@mode: R3
```

<!-- LIVE_STATUS_BEGIN -->
状态: pending | 进度: 0/16 (0%) | 更新: 2026-02-24 23:32:05
当前: -
<!-- LIVE_STATUS_END -->

## 进度概览

| 完成 | 失败 | 跳过 | 总数 |
|------|------|------|------|
| 0 | 0 | 0 | 16 |

---

## 任务列表

### 1. 边界澄清（文档 + 规则）

- [ ] 1.1 更新 `README.md`：明确 `common` 语义=platform-runtime（运行期平台层），禁止承载域语义/服务特有逻辑
- [ ] 1.2 更新 `docs/SYSTEM_DESIGN.md`：补充“common 不按服务名分支 / 新增启动校验必须服务自带”的硬约束
- [ ] 1.3（可选）输出一次性“模块改名（common → platform-runtime）”影响清单与迁移步骤（为方案B预留）

### 2. StartupValidation SPI 化（平台只聚合，服务自管）

- [ ] 2.1 在 `common/src/main/java/com/nowcoder/community/platform/startup/` 增加 `StartupCheck` 接口（约定 fail-closed）
- [ ] 2.2 在 `common/src/main/java/com/nowcoder/community/platform/startup/` 增加 `StartupValidationRunner`（prod profile 下收集并执行所有 `StartupCheck`）
  - 依赖: 2.1
- [ ] 2.3 将 `common/src/main/java/com/nowcoder/community/platform/startup/StartupValidation.java` 重构为“通用校验实现”（移除 `switch(appName)`；保留跨服务通用校验）
  - 依赖: 2.1, 2.2
- [ ] 2.4 在 `auth-service` 新增 `AuthStartupCheck`（或同等 Bean）承接原来 auth-service 的服务特有校验
  - 依赖: 2.1, 2.2
- [ ] 2.5 为 SPI 机制补充单元测试（建议放在 `common/src/test/java/.../startup/`），覆盖：Runner 调用顺序、错误聚合、prod gating

### 3. Dubbo 扫描收敛（避免误暴露与误扫描）

- [ ] 3.1 将各服务 `application.yml` 的 `dubbo.scan.base-packages` 从 `com.nowcoder.community` 收敛到服务根包（如 `com.nowcoder.community.content`）
- [ ] 3.2 增加 gate test：禁止 `dubbo.scan.base-packages=com.nowcoder.community`（建议放在 `common/src/test/java/com/nowcoder/community/platform/arch/`）
  - 依赖: 3.1
- [ ] 3.3（可选）增加 gate test：禁止在 `common`/`infra-*`/`*-api` 中出现 `@DubboService`（仅允许在 `*-service` 的 RPC provider 包中出现）

### 4. prod 配置导入 fail-fast 对齐（消除静默退化）

- [ ] 4.1 为 `ops-service` 补齐 `ops-service/src/main/resources/application-prod.yml`（required 导入 `nacos:${spring.application.name}.yaml`）
- [ ] 4.2 增加 gate test：所有受管理服务必须具备 prod 的 required 导入策略（允许 `-prod.yaml` optional，但基础 `${spring.application.name}.yaml` 必须 required）
  - 依赖: 4.1
- [ ] 4.3（可选）在 StartupCheck 中补充“关键属性必须来自配置中心”的检查（避免关键项落回本地默认值而不自知）

### 5. 验证与回滚预案

- [ ] 5.1 运行 `mvn test`（根目录）确保新增 gate tests 通过，且无历史门禁回归
- [ ] 5.2 编写简要回滚指引（可记录在 proposal 的执行备注）：SPI 化若有问题可临时回退旧逻辑；Dubbo 扫描若漏扫则临时扩大到服务根包父级并修正包结构

---

## 执行日志

| 时间 | 任务 | 状态 | 备注 |
|------|------|------|------|

---

## 执行备注

> 记录执行过程中的重要说明、决策变更、风险提示等
