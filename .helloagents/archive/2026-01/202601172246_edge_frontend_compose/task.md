# Task List: docker compose 集成前端（edge 统一入口）

Directory: `.helloagents/archive/2026-01/202601172246_edge_frontend_compose/`

---

## 1. Compose 集成 edge（前端 + 统一入口）
- [√] 1.1 在 `deploy/docker-compose.yml` 增加 `edge` 服务（基于 `deploy/Dockerfile.edge`），对外仅暴露 `8080`，并将 `/api` 反代到 `gateway`
- [√] 1.2 取消 `gateway` 的宿主机端口映射（仅在 compose 网络内提供服务）

## 2. 回归脚本与运行手册同步
- [√] 2.1 回归脚本：启动服务列表包含 `edge`，并将“外部可达性检查”改为访问 `http://localhost:8080/`
- [√] 2.2 更新运行手册：明确 `8080` 为 edge 统一入口（UI + `/api`）

## 3. 验证
- [√] 3.1 `docker compose config -q` 通过
- [√] 3.2 `docker compose build edge` 通过
