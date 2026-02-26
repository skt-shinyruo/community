# 任务清单: maven-domain-modules-grouping

```yaml
@feature: maven-domain-modules-grouping
@created: 2026-02-26
@status: completed
@mode: R3
```

<!-- LIVE_STATUS_BEGIN -->
状态: completed | 进度: 23/25 (92%) | 更新: 2026-02-26 16:05:29
当前: 全部任务完成（10.3 因网络问题跳过）
<!-- LIVE_STATUS_END -->

## 进度概览

| 完成 | 失败 | 跳过 | 总数 |
|------|------|------|------|
| 23 | 0 | 2 | 25 |

---

## 任务列表

### 1. 基线与全局扫描（先找出“会坏”的地方）

- [√] 1.1 在 `pom.xml` 中确认当前 `<modules>`：包含 5 域的 `*-api`/`*-service`（analytics/content/search/social/user），并明确本次不迁移其它模块（如 `auth-service`、`message-service`、`ops-service` 等）
- [√] 1.2 全仓扫描旧模块路径硬编码（至少覆盖 `docs/`、`deploy/`、`scripts/`）：关键词 `content-service/`、`content-api/`、`social-api/`、`social-service/`、`search-*`、`analytics-*`、`user-*`，记录命中位置清单（用于迁移后逐项修复）
- [√] 1.3 确认 `deploy/Dockerfile.spring-service` 与 `deploy/docker-compose.yml` 当前的 `MODULE` 传参方式（现状：目录路径 + `cp "${MODULE}/target/*.jar"`），为“Docker 去路径耦合”制定落地方式
- [√] 1.4 迁移前执行一次 `./mvnw -q -DskipTests validate`（根目录）作为构建基线；若失败先解决基线问题再开始迁移

### 2. analytics 域：新增聚合 parent + 迁移目录

- [√] 2.1 新增 `analytics/pom.xml`（`packaging=pom`）：parent 指向 `com.nowcoder.community:community`，并聚合子模块 `analytics-api`、`analytics-service`
- [√] 2.2 迁移目录并调整 parent：
  - 移动 `analytics-api` → `analytics/analytics-api`
  - 移动 `analytics-service` → `analytics/analytics-service`
  - 更新 `analytics/analytics-api/pom.xml` 与 `analytics/analytics-service/pom.xml`：`<parent>` 改为 `com.nowcoder.community:analytics:0.0.1-SNAPSHOT`（leaf GAV 不变）
  - 依赖: 2.1

### 3. content 域：新增聚合 parent + 迁移目录

- [√] 3.1 新增 `content/pom.xml`（`packaging=pom`）：parent 指向 `com.nowcoder.community:community`，并聚合子模块 `content-api`、`content-service`
- [√] 3.2 迁移目录并调整 parent：
  - 移动 `content-api` → `content/content-api`
  - 移动 `content-service` → `content/content-service`
  - 更新 `content/content-api/pom.xml` 与 `content/content-service/pom.xml`：`<parent>` 改为 `com.nowcoder.community:content:0.0.1-SNAPSHOT`（leaf GAV 不变）
  - 依赖: 3.1

### 4. search 域：新增聚合 parent + 迁移目录

- [√] 4.1 新增 `search/pom.xml`（`packaging=pom`）：parent 指向 `com.nowcoder.community:community`，并聚合子模块 `search-api`、`search-service`
- [√] 4.2 迁移目录并调整 parent：
  - 移动 `search-api` → `search/search-api`
  - 移动 `search-service` → `search/search-service`
  - 更新 `search/search-api/pom.xml` 与 `search/search-service/pom.xml`：`<parent>` 改为 `com.nowcoder.community:search:0.0.1-SNAPSHOT`（leaf GAV 不变）
  - 依赖: 4.1

### 5. social 域：新增聚合 parent + 迁移目录

- [√] 5.1 新增 `social/pom.xml`（`packaging=pom`）：parent 指向 `com.nowcoder.community:community`，并聚合子模块 `social-api`、`social-service`
- [√] 5.2 迁移目录并调整 parent：
  - 移动 `social-api` → `social/social-api`
  - 移动 `social-service` → `social/social-service`
  - 更新 `social/social-api/pom.xml` 与 `social/social-service/pom.xml`：`<parent>` 改为 `com.nowcoder.community:social:0.0.1-SNAPSHOT`（leaf GAV 不变）
  - 依赖: 5.1

### 6. user 域：新增聚合 parent + 迁移目录

- [√] 6.1 新增 `user/pom.xml`（`packaging=pom`）：parent 指向 `com.nowcoder.community:community`，并聚合子模块 `user-api`、`user-service`
- [√] 6.2 迁移目录并调整 parent：
  - 移动 `user-api` → `user/user-api`
  - 移动 `user-service` → `user/user-service`
  - 更新 `user/user-api/pom.xml` 与 `user/user-service/pom.xml`：`<parent>` 改为 `com.nowcoder.community:user:0.0.1-SNAPSHOT`（leaf GAV 不变）
  - 依赖: 6.1

### 7. Root reactor 调整（root 只聚合域模块）

- [√] 7.1 更新 `pom.xml` 的 `<modules>`：移除对上述 10 个 leaf 模块的引用，新增 5 个域聚合模块 `analytics`、`content`、`search`、`social`、`user`（其余模块条目保持不变）
  - 依赖: 2.2, 3.2, 4.2, 5.2, 6.2
- [√] 7.2 迁移后执行 `./mvnw -q -DskipTests validate`（根目录），快速验证 reactor/parent/modules 均可解析
  - 依赖: 7.1

### 8. Docker 构建去路径耦合（迁移后不改 compose 的 MODULE 值）

- [√] 8.1 更新 `deploy/Dockerfile.spring-service`：
  - 将 `ARG MODULE` 语义调整为 Maven `artifactId`（例如 `content-service`）
  - 构建使用 `mvn -q -DskipTests -pl ":${MODULE}" -am package`
  - 产物拷贝不再依赖模块目录层级：按 `*/${MODULE}/target/*.jar` 查找 jar（排除 `original-*.jar`、`*-sources.jar`、`*-javadoc.jar`、`*-tests.jar`）并拷贝到 `/workspace/app.jar`（找不到则 fail-fast）
- [√] 8.2 更新 `deploy/README.md`：说明 `MODULE` 取值为 artifactId（而非目录路径），并校验 `deploy/docker-compose.yml` 现有 `MODULE: xxx-service` 无需调整
  - 依赖: 8.1

### 9. 文档/脚本引用修复（解决已知硬编码 + 全仓兜底）

- [√] 9.1 更新 `docs/SYSTEM_DESIGN.md`：将 `content-service/src/test/...` 更新为 `content/content-service/src/test/...`（已知命中：line 109）
- [√] 9.2 更新 `docs/DATA_MODEL.md`：将 `content-api/src/...`、`social-api/src/...` 更新为 `content/content-api/src/...`、`social/social-api/src/...`（已知命中：line 87-88）
- [√] 9.3 基于 1.2 的命中清单，全仓修复其余旧路径引用；修复完成后再次 `rg` 校验无残留，并把“修复点清单”记录到本文件「执行备注」

### 10. 验证与收尾（以 mvnw test 为最终门槛）

- [-] 10.1 运行 `./mvnw -q test`（根目录）并记录结果到「执行日志」（通过/失败、关键错误信息）（重复项，以下 10.1 为准）
  - 依赖: 7.2, 9.3
- [√] 10.1 运行 `./mvnw -q test`（根目录）并记录结果到「执行日志」（通过/失败、关键错误信息）
  - 依赖: 7.2, 9.3
- [-] 10.2 若出现 Testcontainers 相关失败（可能需要 Docker）：明确当前环境是否具备 Docker；如不具备，调整相关测试为 profile/条件执行，保证默认 `./mvnw -q test` 稳定通过
  - 依赖: 10.1
- [-] 10.3 进行一次镜像构建冒烟验证（任选一个迁移域服务）：`docker build --build-arg MODULE=content-service -f deploy/Dockerfile.spring-service .`，确认 jar 拷贝路径正确（本次因 Docker Hub 连接超时跳过）
  - 依赖: 8.2
- [√] 10.4 收尾检查：抽样核对被迁移 leaf 模块 `groupId/artifactId/version` 未变化；补充必要的结构说明（可在 `docs/` 或 `deploy/README.md`），并在 proposal 中更新“执行备注/风险收敛”结论

---

## 执行日志

| 时间 | 任务 | 状态 | 备注 |
|------|------|------|------|
| 2026-02-26 15:12:53 | 9.1 | [√] | 修复 `docs/SYSTEM_DESIGN.md`：示例路径补齐 `content/` 前缀 |
| 2026-02-26 15:12:53 | 9.2 | [√] | 修复 `docs/DATA_MODEL.md`：事件契约路径补齐 `content/`、`social/` 前缀 |
| 2026-02-26 15:20:10 | 9.3 | [√] | 修复其余旧路径引用：README 模块路径、DATA_MODEL 的服务列表分隔符；并通过 `rg` 复核无残留 |
| 2026-02-26 15:24:55 | 10.1 | [√] | 通过：`MAVEN_OPTS=-Dmaven.user.home=$PWD/.m2 ./mvnw -q test` |
| 2026-02-26 15:24:55 | 10.2 | [-] | 未出现 Testcontainers 相关失败（本次无需处理） |
| 2026-02-26 16:05:29 | 10.3 | [-] | `docker build` 拉取 `eclipse-temurin:17-jre-jammy` 时出现 `TLS handshake timeout`（网络不稳定/受限），已跳过 |
| 2026-02-26 16:05:29 | 10.4 | [√] | 补齐 KB 路径口径（`.helloagents/*.md`）、并抽样核对 leaf 模块坐标不变 |

---

## 执行备注

> 记录执行过程中的重要说明、决策变更、风险提示等

- 9.3 修复点清单：
  - `README.md`：将 `*-service/` 目录引用更新为 `{domain}/{domain}-service/`（按域分组后的新路径）
  - `docs/DATA_MODEL.md`：将 `social-service/content-service/analytics-service` 改为 `social-service、content-service、analytics-service`（避免误解为目录路径）
