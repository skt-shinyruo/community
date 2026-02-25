# CI 与回归入口细化（建议：GitHub Actions）

Directory: `.helloagents/archive/2026-01/202601161428_boot3_ms_vue3_nacos/`

> 目标：把 CI 从“方向”落到“可执行门禁”，并与 `acceptance.md` 的 DoD 对齐。  
> 默认选择：GitHub Actions（若你的仓库在 GitLab，可按同样结构映射到 `.gitlab-ci.yml`）。

---

## 1. CI 平台与文件位置

- 平台：GitHub Actions
- 工作流文件：`.github/workflows/ci.yml`
- 触发：
  - `pull_request`（必跑门禁）
  - `push`（主干/分支可按需）

---

## 2. 必跑门禁 Jobs（迭代 0 起就要有）

### 2.1 backend-build（必跑）
- Java：17（Temurin）
- 命令：`mvn -q -DskipTests package`
- 目的：保证多模块结构不破坏构建

### 2.2 backend-test（必跑）
- 命令：`mvn -q test`
- 目的：保证基础单测/轻量集成测试可回归

### 2.3 frontend-lint-build（必跑）
- Node：20 LTS
- 包管理：npm（已落地 `package-lock.json`）
- 命令：
  - `npm -C frontend ci`
  - `npm -C frontend run build`

---

## 3. 可选但强烈推荐 Jobs（迭代 0 后逐步打开）

### 3.1 integration（迭代 1 起逐步打开）
- 方案 A：Testcontainers（推荐，隔离性好）
- 方案 B：docker compose（更直观，但 CI 环境差异更大）
- 覆盖：
  - Kafka 事件生产/消费契约
  - ES 索引写入/查询
  - Redis 统计/关系数据

---

## 4. 合并门禁（Branch Protection 对齐）

建议把以下 job 设为 Required checks：
- `backend-build`
- `backend-test`
- `frontend-lint-build`

---

## 5. 缓存策略（降低 CI 成本）

- Maven 缓存：`~/.m2/repository`
- pnpm 缓存：`~/.pnpm-store`（或 actions/cache）
