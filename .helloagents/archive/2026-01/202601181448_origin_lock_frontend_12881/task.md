# Task List: 固定前端端口=12881，并仅允许该 Origin

Directory: `.helloagents/archive/2026-01/202601181448_origin_lock_frontend_12881/`

---

## 1. 固定前端端口（本地 dev 与 docker preview 一致）
- [√] 1.1 将 Vite dev server 端口调整为 `12881`（避免 5173/5174 漂移），更新 `frontend/vite.config.js`

## 2. 仅允许前端 Origin（http://localhost:12881）
- [√] 2.1 gateway：CORS 仅允许 `http://localhost:12881`，移除 `5173/5174` 等其它 origin，更新 `gateway/src/main/resources/application.yml`
- [√] 2.2 auth-service：Origin 白名单仅允许 `http://localhost:12881`，更新 `auth-service/src/main/resources/application.yml`

## 3. 文档与验证
- [√] 3.1 更新运行说明：本地 HMR/Compose 启动方式与端口约束，更新 `deploy/README.md` 与 `.helloagents/modules/frontend.md`
- [√] 3.2 更新知识库与变更记录：补充“Origin/端口策略”的默认值，更新 `.helloagents/modules/gateway.md` 与 `.helloagents/CHANGELOG.md`
- [√] 3.3 执行验证：`mvn -pl auth-service -am test`、`npm -C frontend test`（必要时补充 `mvn -pl gateway -am test`）
