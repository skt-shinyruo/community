# 任务清单（Lightweight Iteration）

目标：本地环境支持“前端直连 gateway”部署方式（前端 `12881` + gateway `12882`），不依赖 edge(Nginx)。

## Tasks
- [√] 前端 http client 支持 `VITE_API_BASE_URL`，并在本地 `localhost/127.0.0.1` 场景下默认推导到 `http://<host>:12882`
- [√] gateway CORS 增加 `http://localhost:12881` / `http://127.0.0.1:12881`
- [√] 新增前端容器镜像：Node + Vite preview（无 Nginx）
- [√] 新增 compose 覆盖文件：暴露前端 `12881` 与 gateway `12882`，并将 edge 设为 profile（默认不启动）
- [√] 更新知识库：frontend/gateway/infra 模块说明 + changelog
- [√] 本地验证：`12881` 可访问；`12882` API 返回 200；CORS 预检通过

## Verification
- HTTP：`GET http://localhost:12881/` -> 200
- HTTP：`GET http://localhost:12882/api/posts?...` -> 200
- CORS：`OPTIONS http://localhost:12882/api/posts` with `Origin: http://localhost:12881` -> `Access-Control-Allow-Origin: http://localhost:12881`

