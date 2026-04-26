# analytics 埋点接入现状说明

这篇文档只回答一个问题：

- UV / DAU 在当前代码里到底是怎么“写进去”的？

结论先说：

- 当前代码里有完整的统计查询与 Redis 存储实现
- Phase 1 已经加入自动 UV / DAU 采集入口，并由 `analytics.ingest.enabled` 控制是否启用
- 生产主配置默认 `analytics.ingest.enabled=false`，测试配置默认启用

也就是说，analytics 现在是一个“会算、会查、可自动采集”的模块；生产是否采集取决于配置开关。

## 1. 当前 analytics 暴露了什么

### 1.1 对外 HTTP 面只有查询

当前 controller 只有：

- `GET /api/analytics/uv`
- `GET /api/analytics/dau`
- `GET /api/analytics/me`

对应代码：

- `backend/community-app/src/main/java/com/nowcoder/community/analytics/controller/AnalyticsController.java`

这里没有任何：

- `POST /api/analytics/track`
- `POST /api/analytics/uv`
- `POST /api/analytics/dau`

之类的写接口。

`/api/analytics/**` 仍然是 query / admin-only 面，不是公共 beacon endpoint。

### 1.2 当前默认配置

`community-app` 当前主配置里，analytics 相关默认值是：

- `analytics.max-days-range = 31`
- `analytics.ingest.enabled = false`
- `analytics.ingest.record-uv = true`
- `analytics.ingest.record-dau = true`
- `analytics.ingest.include-paths = /api/posts/**, /api/search/**, /api/messages/**, /api/notices/**, /api/im-governance/**`
- `analytics.ingest.exclude-paths = /api/analytics/**, /api/auth/**, /api/ops/**, /actuator/**, /internal/**, /files/**`

测试配置保持同一组采集规则，但 `analytics.ingest.enabled = true`，用于覆盖 Phase 1 自动采集链路。

analytics 存储实现固定为 Redis repository，不再提供 `analytics.storage` 切换项。Redis 仍然是当前唯一的 analytics 存储实现。

### 1.3 访问权限是后台权限，不是埋点权限

`AnalyticsSecurityRules` 规定：

- `/api/analytics/**` 只允许 `ADMIN` 或 `MODERATOR`

这进一步说明 analytics 当前的公开职责是：

- 后台查询报表

不是：

- 前台埋点接收器
- 公共 beacon 收集接口

## 2. 写入 API 和自动采集入口

`AnalyticsService` 当前提供两个写方法：

- `recordUv(LocalDate date, String ip)`
- `recordDau(LocalDate date, int userId)`

Phase 1 在 service 写入 API 之上新增了自动采集层：

- `AnalyticsRequestCaptureFilter`
- `AnalyticsRequestClassifier`
- `AnalyticsPrincipalResolver`
- `AnalyticsIngestService`
- `AnalyticsUserOrdinalRepository`
- `RedisAnalyticsUserOrdinalRepository`

请求流量由 filter 在下游请求完成后判断是否采集；登录成功由 `AuthService.login(...)` 补记 DAU。

### 2.1 写入 API 当前几乎不做语义校验

和查询不同，`recordUv(...)` / `recordDau(...)` 当前基本只是把参数直接转交给 repository。

这意味着：

- analytics service 写入 API 不负责判断哪些请求算 UV / DAU
- 采集语义由 Phase 1 ingest 层负责
- repository 负责把最终 date / ip / ordinal 写入 Redis

## 3. 当前仓库里的真实调用点

### 3.1 生产代码调用点

生产代码里已经有两个采集调用路径：

- `AnalyticsRequestCaptureFilter` 对配置允许的请求调用 `AnalyticsIngestService.recordRequest(ip, userId)`
- `AuthService.login(...)` 成功后调用 `AnalyticsIngestService.recordLoginSuccess(userId)`

`AnalyticsIngestService` 根据配置决定是否写 UV / DAU。写入失败时只记录降噪后的日志，不影响主业务响应。

### 3.2 测试覆盖

当前能看到的测试覆盖包括：

- `backend/community-app/src/test/java/com/nowcoder/community/analytics/service/AnalyticsServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/analytics/repo/RedisAnalyticsRepositoryTest.java`
- Phase 1 ingest 相关 filter / classifier / service / ordinal repository 测试
- `AuthService.login(...)` 成功后补记 DAU 的测试

这些测试覆盖：

- `AnalyticsService` 把写入调用委托给 repository
- `RedisAnalyticsRepository` 把 UV 写入 Redis HyperLogLog，把 DAU 写入 Redis bitmap
- 请求采集规则、登录补记、失败降级和 UUID 到整数 ordinal 的适配

## 4. 这意味着什么

当前 analytics 模块的状态可以概括成一句话：

- query path 和 Redis storage path 已经存在，Phase 1 ingest path 也已存在但由配置开关控制

也就是：

- 查统计走 `/api/analytics/**`
- 自动采集走请求 filter 和登录成功补记
- 生产默认不启用自动采集
- 测试默认启用自动采集

如果线上部署后仍保持主配置默认值，那么 UV / DAU 不会因为普通业务请求自动增长；需要显式开启 `analytics.ingest.enabled=true`。

## 5. UV 写入口径

### 5.1 UV 依赖请求入口解析 IP

`recordUv(date, ip)` 本身不会从 `HttpServletRequest` 解析：

- `RemoteAddr`
- `X-Forwarded-For`
- 代理链头

Phase 1 请求采集由 `AnalyticsRequestCaptureFilter` 通过现有 `ClientIpResolver` 解析客户端 IP，再由 `AnalyticsIngestService` 写入当日 UV。

### 5.2 当前 UV 采集规则

UV 的当前口径是：

- `analytics.ingest.enabled=true`
- `analytics.ingest.record-uv=true`
- 请求不是 `OPTIONS`
- 响应状态不是 `5xx`
- 请求路径命中 include path
- 请求路径没有命中 exclude path
- 解析到非空客户端 IP

符合规则的请求按客户端 IP 写入当日 Redis HyperLogLog。

## 6. DAU 写入口径

### 6.1 DAU 仍然落到整数 bitmap offset

`recordDau(date, userId)` 的底层存储仍然使用 Redis bitmap，所以写入 API 需要整数 bit offset。

当前 JWT subject 是 UUID。Phase 1 通过 `RedisAnalyticsUserOrdinalRepository` 把 UUID 映射为 analytics-only 整数 ordinal，再写入 DAU bitmap。

ordinal 映射使用 Redis key：

- `{analytics:user-ordinal}:map`
- `{analytics:user-ordinal}:seq`

这两个 key 使用同一个 Redis Cluster hash tag，Lua 脚本在 Redis Cluster 下可以落在同一个 slot。

### 6.2 analytics 自动采集入口

Phase 1 后，analytics 通过 `AnalyticsRequestCaptureFilter` 自动采集被配置允许的请求。

当前口径：

- UV：符合采集规则的请求按客户端 IP 写入当日 HyperLogLog
- DAU：符合采集规则且存在有效 JWT UUID subject 的请求，先映射到 analytics-only 整数 ordinal，再写入当日 bitmap
- 登录成功：`AuthService.login(...)` 成功后补记一次 DAU
- Redis 写入失败：只记录降噪后的日志，不影响主业务响应

默认采集范围包括 `/api/posts/**`、`/api/search/**`、`/api/messages/**`、`/api/notices/**`、`/api/im-governance/**`。

`/api/analytics/**`、`/api/auth/**`、`/api/ops/**`、`/actuator/**`、`/internal/**`、`/files/**` 默认不由请求 filter 采集。

## 7. `/api/analytics/me` 的真实作用

`GET /api/analytics/me` 当前只做一件事：

- 返回 `CurrentUser.requireJwt(authentication).getSubject()`

它更像一个：

- 鉴权联调接口
- 后台权限检查接口

不是埋点写入路径的一部分。

## 8. 为什么这会让人误判

analytics 模块有两类入口：

- `/api/analytics/**`：后台查询 / 权限验证入口
- `AnalyticsRequestCaptureFilter` 和 `AuthService.login(...)`：内部自动采集入口

不要把 `/api/analytics/**` 误读成前台 beacon 接收器。Phase 1 没有新增公共埋点 HTTP 写接口。

## 9. 真正已经落好的存储抽象

这部分已经在 [analytics-uv-dau-flow.md](./analytics-uv-dau-flow.md) 里展开，这里只强调与埋点相关的结论。

### 9.1 一旦采集入口启用，模块会写入 Redis

当前写路径启用后，底层已经准备好了：

- UV：Redis HyperLogLog
- DAU：Redis Bitmap
- UUID 到 int ordinal：Redis hash + sequence

### 9.2 模块边界

analytics 当前的边界是：

- request filter 决定哪些请求尝试采集
- ingest service 根据开关决定是否记录 UV / DAU
- ordinal repository 负责 UUID 到整数 ordinal 的稳定映射
- analytics repository 负责 Redis 存储
- `/api/analytics/**` 负责后台查询，不负责写入 beacon

## 10. 为什么这也是一条值得写文档的核心逻辑

因为这是一个很容易被误判的模块。

很多人看到：

- `AnalyticsController`
- `AnalyticsService`
- `RedisAnalyticsRepository`

会自然认为 analytics 的采集入口也在 `/api/analytics/**` 下面。

实际边界是：

- 查询入口在 controller
- 自动采集入口在 filter 和登录成功路径
- 生产默认关闭自动采集

把这个边界写清楚很重要，否则后面排查“为什么 UV / DAU 一直是 0”时会忽略 `analytics.ingest.enabled`。

## 11. 后续可能扩展的接入位置

下面这些是后续可能的扩展方向，不是 Phase 1 已经新增的公共 API：

- gateway / edge 层把埋点沉到独立 collector，再异步写 analytics
- 前端 beacon endpoint
- 更细粒度的事件埋点

Phase 1 的当前事实是：请求 filter 和登录成功补记已经存在，公共 beacon endpoint 没有新增。

## 12. 对初学者最实用的判断

理解 analytics 时，先分清三层：

- 统计查询层：已经有
- Redis 存储层：已经有
- 自动采集层：Phase 1 已经有，但生产默认由 `analytics.ingest.enabled=false` 关闭

只要这个判断错了，后面对数据异常的定位方向就会完全跑偏。

## 13. 进一步阅读

如果你要顺着当前已实现部分继续看，建议按这个顺序：

1. `analytics-uv-dau-flow.md`
2. `security-authz-boundary-flow.md`
3. `startup-fail-closed-runtime-flow.md`

第一篇看“怎么算”，后两篇看“谁能查”和“系统是怎么被装起来的”。

## 14. 关键代码定位

- `backend/community-app/src/main/java/com/nowcoder/community/analytics/controller/AnalyticsController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/analytics/ingest/AnalyticsRequestCaptureFilter.java`
- `backend/community-app/src/main/java/com/nowcoder/community/analytics/ingest/AnalyticsIngestService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/analytics/ingest/AnalyticsIngestProperties.java`
- `backend/community-app/src/main/java/com/nowcoder/community/analytics/repo/AnalyticsUserOrdinalRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/analytics/repo/RedisAnalyticsUserOrdinalRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/analytics/service/AnalyticsService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/analytics/repo/AnalyticsRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/analytics/repo/RedisAnalyticsRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/analytics/security/AnalyticsSecurityRules.java`
