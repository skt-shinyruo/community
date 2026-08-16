# Gateway Runtime

`community-gateway` 直接使用 Spring Cloud Gateway 的原生 route 配置和刷新机制，不维护第二套路由模型。

## HTTP 和 IM edge routes

routes 配置在 `spring.cloud.gateway.server.webflux.routes`：

- `POST /api/im/sessions` 以 `order=-100` 转到 `lb://community-im-gateway`。
- `/ws/im` 且 `Upgrade: websocket` 的请求以 `order=-100` 转到 `lb:ws://community-im-gateway`。
- `/api/oss` 和 `/api/oss/**` 以 `order=-20` 转到 `lb://community-oss`。
- `/api/im` 和 `/api/im/**` 以 `order=-10` 转到 `lb://im-core`。
- `/api` 和 `/api/**` 转到 `lb://community-app`，因此不会抢先命中更具体的 OSS、IM route。
- `/files` 和 `/files/**` 转到 `lb://community-oss`。

公开 route 不配置 `/internal/**`。`spring.cloud.gateway.server.webflux.default-filters` 使用原生 `DedupeResponseHeader`，保留第一份常见 CORS 响应头。

## 动态刷新

Spring Cloud Gateway 默认启用内置 `RouteRefreshListener`。refresh scope 完成、服务实例注册或 discovery heartbeat 变化时，它发布 `RefreshRoutesEvent` 并重建 route cache；仓库不再维护自定义事件桥接。
