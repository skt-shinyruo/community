# Gateway Runtime

`community-gateway` 的运行时配置集中在 HTTP route、IM edge route 和动态刷新 hook 上。浏览器流量按配置生成 Spring Cloud Gateway routes，配置刷新监听器在相关 key 变化后触发路由重建。

## HTTP 和 IM edge routes

`GatewayRouteLocatorConfig` 读取 `gateway.http.routes`，按 `path-prefix` 长度倒序建 route。较长前缀优先，例如 `/api/im` 会先于 `/api` 命中。空 route、非公开 `/internal*` 前缀和缺少 `service-id` 的配置会被跳过；有效 route 转到 `lb://<serviceId>`，并在 gateway 层去重常见 CORS 响应头。

`GatewayImEdgeRouteConfig` 读取 `gateway.im-edge.*`。当前默认：

- `service-id=community-im-gateway`
- `session-path=/api/im/sessions`
- `ws-path=/ws/im`

它为 `POST /api/im/sessions` 建立优先级更高的 HTTP route，转到 `lb://community-im-gateway`；为 `/ws/im` 且 `Upgrade: websocket` 的请求建立 WebSocket route，转到 `lb:ws://community-im-gateway`。同样会拒绝 `/internal*` 作为公开路径。

## 动态刷新

`GatewayConfigRefreshListener` 监听两类 Spring Cloud 事件：

1. `EnvironmentChangeEvent`：只要变化 key 属于 `gateway.http.routes` 或 `gateway.im-edge`，就设置一次 route refresh pending 标记。前缀匹配要求完整前缀，或后面紧跟 `.` / `[`，所以 `gateway.http.routes-extra`、`gateway.im-edge-debug` 不会误触发。
2. `RefreshScopeRefreshedEvent`：如果 pending 标记存在，就发布一次 `RefreshRoutesEvent` 并清除标记。

这意味着 HTTP route 和 IM-edge 配置变更会在 refresh scope 完成后驱动 Spring Cloud Gateway 重建 routes；同一轮 refresh scope 前的多次相关 key 变化会合并成一次 `RefreshRoutesEvent`。无关 key 不触发 route refresh，空 key 集合也不会触发。
