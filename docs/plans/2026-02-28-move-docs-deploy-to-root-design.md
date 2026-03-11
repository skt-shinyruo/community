# Move `backend/docs` + `backend/deploy` to Repo Root (Design)

**Date:** 2026-02-28

## Context / Problem

当前仓库是一个 `frontend/` + `backend/` 的 monorepo。

但 `backend/docs/` 与 `backend/deploy/` 这两个目录从语义上更像“仓库级（全局）入口”：

- `deploy/` 中包含全栈 docker compose（含前端镜像构建），不属于仅后端工程内部细节。
- `docs/` 里描述的是整体架构/运行/观测/安全等，阅读对象也是全栈开发者。

放在 `backend/` 下会造成误导：看起来像“后端私有”，实际是“全仓库入口”。

## Goals

- 将 `backend/docs/` 移动到仓库根目录：`docs/`
- 将 `backend/deploy/` 移动到仓库根目录：`deploy/`
- 统一约定：**文档中的命令默认从仓库根目录执行**
- 修正 docker compose 的 build context/dockerfile 相对路径，使迁移后 `deploy/*` 仍可用
- 更新 README 与主要文档中的路径引用，使其与“仓库根目录为入口”一致

## Non-goals

- 不借此机会重构后端/前端代码结构（除非迁移会直接导致路径错误）
- 不引入额外的发布系统或 CI/CD 变更

## Proposed Changes

### 1) 目录移动（hard migration）

- `backend/docs` → `docs`
- `backend/deploy` → `deploy`

不在 `backend/` 下保留 `docs/` 或 `deploy/` 的兼容跳转目录（避免“双入口”）。

### 2) Docker Compose 路径修正

迁移后 compose 文件位于 `deploy/`，需要保证：

- 后端镜像构建仍以 `backend/` 作为 build context（Dockerfile 依赖 `backend/pom.xml`）
- 前端镜像构建以仓库根作为 build context（Dockerfile 需要 `frontend/*`）

### 3) 文档/README 统一入口

- 根目录 `README.md` 的快速开始不再要求 `cd backend`
- `backend/README.md` 作为“后端工程说明”，但部署/文档入口指向根目录的 `deploy/` 与 `docs/`
- `deploy/README.md` / `docs/*.md` 的“命令默认执行目录”统一为仓库根目录
- 文档中引用后端源代码路径（如 `app/...`, `platform/...`）统一加前缀 `backend/`

### 4) Ignore 规则迁移

将本地敏感文件（如 `.env`）的忽略规则从旧路径扩展到新路径：

- 继续忽略 `backend/deploy/.env`（为已有本地文件保留兼容，避免迁移后突然暴露为 untracked）
- 新增忽略 `deploy/.env`（新入口）

## Migration Notes (Local Files)

如果本地已存在未提交文件：

- `backend/deploy/.env`
- `backend/deploy/.local/`
- `backend/deploy/backups/` 下的备份文件

它们不会自动随 `git mv` 一起迁移（因为通常是 untracked/ignored）。迁移后建议手动移动到根目录的 `deploy/` 下。

## Verification

- `docker compose -f deploy/docker-compose.yml --env-file deploy/.env.example config`
- `cd backend && ./mvnw -q -DskipTests -pl :community-bootstrap -am package`
