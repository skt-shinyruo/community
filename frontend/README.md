# frontend

本目录是社区主站 Vue3 SPA。长期维护的前端核心逻辑说明见 `../docs/handbook/frontend.md`，测试策略见 `../docs/handbook/testing.md`。

## 常用命令

```bash
npm install
npm run dev
npm test
npm run build
```

默认本地入口：

- Vite dev server：`http://localhost:5173`
- compose frontend-nginx：`http://localhost:12881`
- API / files / WS gateway：`http://localhost:12880`

未显式配置 API base URL 时，前端使用同源相对路径；本地 Vite dev / preview 通过 proxy 将 `/api`、`/files` 和 `/ws/im` 转发到 gateway。部署环境应由同源入口转发，或通过 runtime config / Vite env 显式提供 API base URL。

## 目录结构

| 路径 | 责任 |
| --- | --- |
| `src/main.js` | 创建 Vue app、Pinia、router。 |
| `src/App.vue` | 应用壳层、全局布局和 toast。 |
| `src/router/` | 路由表、路由守卫、导航 SSOT。 |
| `src/auth/` | 会话恢复、session hint。 |
| `src/api/` | axios 客户端、Result 处理、API service。 |
| `src/config/` | runtime config 和 endpoint 解析。 |
| `src/im/` | IM WebSocket realtime client。 |
| `src/stores/` | Pinia stores 和轻量读侧缓存。 |
| `src/views/` | 页面组件和页面纯状态模块。 |
| `src/components/` | 可复用 UI / scene / posts 组件。 |
| `src/utils/` | opaque id、时间、JSON、请求竞态等工具。 |

## 核心约定

- 安全授权以后端为准，前端 route guard 只是体验层保护。
- access token 只存在 Pinia 内存，refresh token 由 HttpOnly cookie 承载。
- 主站 HTTP 请求使用 `src/api/http.js`，IM HTTP 使用 `src/api/imCoreHttp.js`。
- IM WebSocket 不硬编码固定地址，必须先 `POST /api/im/sessions` 获取 `wsUrl` 和 ticket。
- 复杂页面状态优先放进 `src/views/*State.js` 并配同名测试。
- 高风险写操作必须保持同一次业务尝试复用同一个 `Idempotency-Key`。

## 定向测试

```bash
npm test -- src/router/authGuard.test.js
npm test -- src/auth/session.test.js
npm test -- src/api/http.test.js src/api/http.resolution.test.js
npm test -- src/im/imRealtimeClient.test.js
npm test -- src/views/marketState.test.js src/views/walletState.test.js
```

更多测试分层和运行建议见 `../docs/handbook/testing.md`。
