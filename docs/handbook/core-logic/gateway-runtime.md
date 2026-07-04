# Gateway Runtime

`community-gateway` 的运行时配置集中在 HTTP route、IM edge route、canary selector 和动态刷新 hook 上。这里只描述当前实现：浏览器流量按配置生成 Spring Cloud Gateway routes，canary 过滤器按服务发现 metadata 选择实例，配置刷新监听器在相关 key 变化后触发路由重建。

## HTTP 和 IM edge routes

`GatewayRouteLocatorConfig` 读取 `gateway.http.routes`，按 `path-prefix` 长度倒序建 route。较长前缀优先，例如 `/api/im` 会先于 `/api` 命中。空 route、非公开 `/internal*` 前缀和缺少 `service-id` 的配置会被跳过；有效 route 转到 `lb://<serviceId>`，并在 gateway 层去重常见 CORS 响应头。

`GatewayImEdgeRouteConfig` 读取 `gateway.im-edge.*`。当前默认：

- `service-id=community-im-gateway`
- `session-path=/api/im/sessions`
- `ws-path=/ws/im`

它为 `POST /api/im/sessions` 建立优先级更高的 HTTP route，转到 `lb://community-im-gateway`；为 `/ws/im` 且 `Upgrade: websocket` 的请求建立 WebSocket route，转到 `lb:ws://community-im-gateway`。同样会拒绝 `/internal*` 作为公开路径。

## Canary instance filtering

`CanaryRouteProperties` 绑定 `gateway.http.canary.rules`，当前 seed 配置为空规则列表。`CanaryInstanceFilter` 的已实现行为是对传入的 `ServiceInstance` 列表做选择：

- null 或空实例列表返回空列表。
- selector 为 null 或 metadata 为空时，允许所有非 draining 实例。
- selector metadata 必须逐项精确匹配实例 metadata；selector key/value 为 null 会导致不匹配。
- 实例 metadata 为 null 时按空 map 处理。
- `draining=true` 的实例会被排除，比较时会 trim 并忽略大小写；`draining=false`、缺失或 null 视为可接收流量。

因此 canary 选择依赖低基数服务发现 metadata，例如 `release.track=canary`、`traffic.group=beta`。`draining=true` 是实例退出新流量的硬排除信号。

## 动态刷新

`GatewayConfigRefreshListener` 监听两类 Spring Cloud 事件：

1. `EnvironmentChangeEvent`：只要变化 key 属于 `gateway.http.routes`、`gateway.im-edge` 或 `gateway.http.canary`，就设置一次 route refresh pending 标记。前缀匹配要求完整前缀，或后面紧跟 `.` / `[`，所以 `gateway.http.routes-extra`、`gateway.im-edge-debug`、`gateway.http.canarying` 不会误触发。
2. `RefreshScopeRefreshedEvent`：如果 pending 标记存在，就发布一次 `RefreshRoutesEvent` 并清除标记。

这意味着 route、canary 和 IM-edge 配置变更会在 refresh scope 完成后驱动 Spring Cloud Gateway 重建 routes；同一轮 refresh scope 前的多次相关 key 变化会合并成一次 `RefreshRoutesEvent`。无关 key 不触发 route refresh，空 key 集合也不会触发。
