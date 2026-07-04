# Runtime Configuration

`/api/runtime-config` 是面向浏览器的运行时配置快照。它让前端在启动时从后端读取当前网关入口、WebSocket 地址、发布通道、功能开关和上传策略，而不是把这些值全部编译进前端包。

## 当前入口

`RuntimeConfigController` 暴露 `GET /api/runtime-config`，并通过 `RuntimeSecurityRules` 允许未登录访问。Controller 只做 HTTP 适配：调用 `RuntimeConfigApplicationService.current()`，再把 `RuntimeConfigResult` 作为 200 响应返回。

这类 Controller 属于 `IndexOnly`：读者需要知道它是公开 runtime snapshot 的 HTTP 入口，但业务行为在 application service 和配置属性里。

## 快照内容

`RuntimeConfigApplicationService` 从 `frontend.runtime.*` 绑定出的 `RuntimeConfigProperties` 组装快照。当前返回字段包括：

- `apiBasePath`：浏览器 API 基础路径，默认 `/api`。
- `publicGatewayOrigin`：浏览器可见的 gateway origin，可为空。
- `websocketUrl`：浏览器连接 IM 的 WebSocket URL，可为空；Nacos seed 中为 `ws://localhost:12880/ws/im`。
- `analyticsEnabled` / `analyticsSampleRate`：前端 analytics 开关和采样率。
- `releaseChannel`：发布通道，默认 `local`。
- `features`：前端功能开关 map，例如 seed 配置中的 `posts`、`comments`、`private-message`、`file-upload`、`market`。
- `upload`：上传策略快照，包含最大文件大小、最大请求大小、允许 MIME、允许扩展名，以及 avatar/media 上传开关。

`RuntimeConfigApplicationService` 会复制 `features`、MIME list 和扩展名 list，避免把内部可变集合直接暴露给响应对象。`RuntimeConfigProperties.Upload` 还会对大小文本和 list 做基础归一化：空大小回退到 `10GB`，list 会过滤空白项并 trim。

## 边界语义

这个 endpoint 只发布浏览器安全的配置快照，不返回密钥、内部主机、凭据或 operator-only 策略。上传策略里的 MIME、扩展名和大小只用于前端展示或预检；真正的上传接收、鉴权和存储限制仍由后端上传链路执行。

`RuntimeConfigApplicationService` 已经覆盖了当前快照组装行为；`RuntimeConfigController` 只需要作为公开入口保留索引。
