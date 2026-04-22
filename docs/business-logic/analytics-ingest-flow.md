# analytics 埋点接入现状说明

这篇文档只回答一个问题：

- UV / DAU 在当前代码里到底是怎么“写进去”的？

结论先说：

- 当前代码里有完整的统计查询与存储实现
- 但没有接好的生产埋点入口

也就是说，analytics 现在是一个“会算、会查”的模块，不是一个已经完整落地的“会自动采集”的模块。

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

### 1.2 当前默认配置

`community-app` 当前主配置里，analytics 相关默认值是：

- `analytics.storage = redis`
- `analytics.max-days-range = 31`

### 1.3 访问权限是后台权限，不是埋点权限

`AnalyticsSecurityRules` 规定：

- `/api/analytics/**` 只允许 `ADMIN` 或 `MODERATOR`

这进一步说明 analytics 当前的公开职责是：

- 后台查询报表

不是：

- 前台埋点接收器
- 公共 beacon 收集接口

## 2. 写入 API 其实存在，但只在 service 层

`AnalyticsService` 当前提供两个写方法：

- `recordUv(LocalDate date, String ip)`
- `recordDau(LocalDate date, int userId)`

这意味着 analytics 域已经定义了“如果有人来写，应该怎么写”。

但要注意：

- 它们不是 controller
- 也不是 filter
- 也不是 event listener

只是普通 Spring service 方法。

### 2.1 写入 API 当前几乎不做语义校验

和查询不同，`recordUv(...)` / `recordDau(...)` 当前基本只是把参数直接转交给 repository。

这意味着：

- analytics 模块并不负责保证埋点输入质量
- 真正的调用方如果以后接线，必须自己保证 date / ip / userId 的语义正确

## 3. 当前仓库里有没有真实调用点

### 3.1 生产代码里没有

全仓库搜索结果表明，`recordUv(...)` 和 `recordDau(...)` 在生产代码里的出现位置只有：

- `AnalyticsService`
- `AnalyticsRepository`
- `RedisAnalyticsRepository`
- `InMemoryAnalyticsRepository`

没有任何生产代码调用：

- `analyticsService.recordUv(...)`
- `analyticsService.recordDau(...)`

这不是“代码藏得深没找到”，而是当前仓库搜索结果的直接结论。

### 3.2 只有测试在调用

当前能看到的真实调用点来自测试：

- `backend/community-app/src/test/java/com/nowcoder/community/analytics/service/AnalyticsServiceTest.java`

测试里会手动调用：

- `service.recordUv(d1, "1.1.1.1")`
- `service.recordDau(d1, 1)`

这说明：

- 存储和聚合逻辑是被测试覆盖的
- 但线上接入路径没有在当前仓库里落地

## 4. 这意味着什么

当前 analytics 模块的状态可以概括成一句话：

- query path 是完整的，ingest path 是缺失的

也就是：

- 查统计没问题
- 但谁负责把访问和活跃写进去，目前没有接线

如果线上真部署这套代码而不额外补埋点接入，那么：

- `/api/analytics/uv`
- `/api/analytics/dau`

理论上只能查到空值或历史测试数据，不会自动增长。

## 4. 当前 ingest 面真正缺的是什么

当前缺的不是：

- Redis 存储结构
- UV / DAU 区间算法
- 查询接口

当前缺的是“采集接线”本身，也就是：

- 谁在请求入口提取 IP
- 谁在登录后或活跃请求里提取 userId
- 谁决定哪些请求算 UV
- 谁决定哪些行为算 DAU
- 这些动作失败时是否重试或降级

换句话说，analytics 当前没有 ingest pipeline，只有 ingest API。

## 5. UV 写入接口当前期待什么输入

### 5.1 UV 依赖调用方自己传 IP

`recordUv(date, ip)` 并不会自己从 `HttpServletRequest` 解析：

- `RemoteAddr`
- `X-Forwarded-For`
- 代理链头

这些都不在 analytics 模块里做。

也就是说，如果未来有人接线，调用方必须自己决定：

- 取哪个 IP
- 是否信任代理头
- 是否做匿名化 / 清洗

analytics 模块只负责落库。

### 5.2 analytics 模块本身没有游客 / 登录态分支

UV 是按 IP 去重，所以从模块本身看：

- 游客也能记
- 登录用户也能记

但前提是上游代码显式调用 `recordUv(...)`。

当前没有这样的上游代码。

## 6. DAU 写入接口当前期待什么输入

### 6.1 DAU 依赖调用方自己传 `int userId`

`recordDau(date, userId)` 也不会自己解析：

- JWT
- SecurityContext
- 当前会话

因此未来若要接线，调用方必须自己先完成：

- 鉴权
- 用户 id 提取
- UUID / int id 适配

再把最终的整数 userId 交给 analytics。

### 6.2 analytics 模块没有“访问即记活跃”的现成钩子

当前没有看到：

- servlet filter
- spring interceptor
- login success listener
- request metrics listener

去调用 `recordDau(...)`。

所以“用户访问就自动记 DAU”这件事，当前代码里并不存在。

## 7. `/api/analytics/me` 的真实作用

`GET /api/analytics/me` 当前只做一件事：

- 返回 `CurrentUser.requireJwt(authentication).getSubject()`

它更像一个：

- 鉴权联调接口
- 后台权限检查接口

不是埋点写入路径的一部分。

## 8.1 为什么这会让人误判

因为当前 analytics 模块已经具备了一个“看起来很完整”的外形：

- controller
- service
- redis repository
- security rules
- test

但它少的恰好是最靠近真实流量入口的那一层，所以非常容易被误读成“只是我还没找到触发点”。

## 9. 真正已经落好的只有存储抽象

这部分已经在 [analytics-uv-dau-flow.md](./analytics-uv-dau-flow.md) 里展开，这里只强调与埋点相关的结论。

### 8.1 一旦有人调用，模块会正常工作

当前写路径一旦被真实接线，底层已经准备好了：

- Redis 版本：HyperLogLog + Bitmap
- memory 版本：HashSet + BitSet

### 8.2 模块不替调用方做采集决策

analytics 当前的边界非常克制：

- 不决定什么时候记 UV
- 不决定什么时候记 DAU
- 不决定游客是否算活跃
- 不决定 IP 怎么抽取

它只做：

- 接收 date / ip / userId
- 交给 repository 存储

## 10. 为什么这也是一条值得写文档的核心逻辑

因为这是一个很容易被误判的模块。

很多人看到：

- `AnalyticsController`
- `AnalyticsService`
- `RedisAnalyticsRepository`

会自然认为 analytics 已经是闭环。

但当前闭环只有：

- 写入后如何存
- 存完后如何查

并没有：

- 谁触发写入

把这个缺口写清楚很重要，否则后面排查“为什么 UV / DAU 一直是 0”时会浪费很多时间。

## 11. 如果后面要接线，最可能的接入位置

下面这些是“从代码结构推断出的合理接入点”，不是当前已实现事实：

- 统一请求过滤器里记录 UV
- 登录成功或已认证请求里记录 DAU
- gateway / edge 层把埋点沉到独立 collector，再异步写 analytics

当前仓库没有实现这些路径，所以这里只能作为后续补文档和补代码的候选方向。

## 12. 对初学者最实用的判断

理解 analytics 时，先分清两层：

- 统计计算层：已经有
- 埋点采集层：当前没有

只要这个判断错了，后面对数据异常的定位方向就会完全跑偏。

## 13. 进一步阅读

如果你要顺着当前已实现部分继续看，建议按这个顺序：

1. `analytics-uv-dau-flow.md`
2. `security-authz-boundary-flow.md`
3. `startup-fail-closed-runtime-flow.md`

第一篇看“怎么算”，后两篇看“谁能查”和“系统是怎么被装起来的”。

## 14. 关键代码定位

- `backend/community-app/src/main/java/com/nowcoder/community/analytics/controller/AnalyticsController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/analytics/service/AnalyticsService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/analytics/repo/AnalyticsRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/analytics/repo/RedisAnalyticsRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/analytics/repo/InMemoryAnalyticsRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/analytics/security/AnalyticsSecurityRules.java`
- `backend/community-app/src/test/java/com/nowcoder/community/analytics/service/AnalyticsServiceTest.java`
