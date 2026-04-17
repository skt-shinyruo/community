# Community Reviewed Issues Hardening Design

## Background

在本次代码审查里，仓库存在 4 个需要直接修复的真实问题：

1. `community-app` 的搜索全量重建互斥只在单 JVM 内生效，集群部署下会并发执行。
2. 登录风控依赖 Redis 的访问方式过于脆弱，轻微抖动就会静默 fail-open。
3. 前端端点解析与架构文档不一致，本地启动前端时会回落到错误地址。
4. `community-gateway` 的所谓“基础限流”使用单实例内存计数，无法满足多副本 edge 的一致性要求。

这些问题都不是“文档不精确”层面的偏差，而是会直接影响线上正确性、安全性或部署可用性的实现缺陷。

## Goal

在不重构整体架构的前提下，修复上述 4 个问题，使实现与当前仓库的集群拓扑、边界文档和安全预期保持一致。

## Non-Goals

- 不重写搜索投影链路或引入新的搜索任务系统。
- 不对认证体系做大范围重构，不改 JWT / refresh token 主流程语义。
- 不把前端 endpoint 解析扩展成完整环境发现框架。
- 不引入新的网关限流产品能力，只把现有限流从单机状态改为共享状态。

## Design

### 1. Search Reindex: distributed single-flight

`ReindexJobService` 目前用 `AtomicReference<String>` 做运行中 jobId 管理，只能防止单进程内重复执行。仓库已经具备基于 Redis 的 `SingleFlightTaskGuard`，因此本次修复不新增第二套锁语义，而是直接复用该组件。

设计约束：

- `ReindexJobService.tryStart()` 改为通过 `SingleFlightTaskGuard.tryAcquire("search:reindex", ttl)` 获取分布式锁。
- `ReindexJob` 需要同时携带 `jobId` 和持有的 lock 句柄，便于 `finish()` 正确释放。
- `finish()` 必须幂等，并且只能释放当前实例持有的 lock。
- 若 Redis 不可用，reindex 必须 fail-closed，返回“已有任务执行中 / 当前无法获取执行权”语义，而不是继续冒险执行。

原因：

- reindex 是高成本破坏性操作，宁可跳过，也不能多节点同时 `clearAndReindex`。
- 这里不需要租约续期；当前执行模型是同步单次操作，先用固定 TTL 覆盖预期执行窗口即可。若后续执行时间增长，再扩展 renewal。

### 2. Login Rate Limit: remove fragile async timeout wrapper

`LoginRateLimitService` 当前把所有 Redis 访问提交到一个小线程池，并用 `dependencyTimeoutMs` 控制超时。这个模式把正常依赖访问变成了“线程池 + 超时 + Redis”三重故障点，且异常被大量吞掉，最终演化为静默 fail-open。

本次修复设计：

- 删除内部 `ThreadPoolExecutor`、`Future#get(timeout)` 和 `dependencyTimeoutMs` 语义。
- 所有计数读写改为同步执行 Redis 调用。
- 依赖异常时明确记录 warning 日志和指标，不再静默吞掉。
- `assertNotBlocked()` / `recordFailure()` / `isCaptchaRequired()` 在依赖异常时统一 fail-closed：
  - 登录主流程返回 `503` 或等价业务错误；
  - 验证码判定不能默认返回“无需验证码”。
- `reset()` 保持 best-effort，但也要记录异常，避免完全无观测。

原因：

- 登录风控失效比短时拒绝登录更危险。
- 当前仓库的安全文档将登录风控描述为核心防护，而不是“尽力而为的提示层”。

### 3. Frontend endpoint resolution: align implementation with documented local topology

当前 `frontend/src/config/endpointResolution.js` 中：

- API / IM HTTP base URL 只读取 runtime config 或 Vite env；
- IM WebSocket 才有基于 `location.host` 的 fallback；
- HTTP fallback 是空串 same-origin。

这与 `docs/ARCHITECTURE.md` 中对本地端口 `5173|12881|12890|12888 -> 12880` 的说明不一致。

本次修复设计：

- 为 API / IM HTTP / IM WS 三者统一引入本地拓扑推导逻辑：
  - 当当前页面 host 为 `localhost` 或 `127.0.0.1`，且端口属于 `5173/12881/12890/12888` 时：
    - API base URL = `http(s)://<host>:12880`
    - IM HTTP base URL = `http(s)://<host>:12880`
    - IM WS URL = `ws(s)://<host>:12880/ws/im`
- runtime config 仍然优先于 Vite env；Vite env 仍然优先于自动推导。
- 非本地场景继续保持 same-origin fallback。

原因：

- 本地开发拓扑是仓库的默认推荐路径，文档和实现必须一致。
- 把自动推导逻辑集中在 `endpointResolution.js`，避免 `http.js`、`imCoreHttp.js`、`imRealtimeClient.js` 各自再做一套判断。

### 4. Gateway rate limit: shared Redis-backed limiter

`community-gateway` 当前通过 `EdgeConfig -> InMemoryRateLimiter` 装配限流，状态只存在单进程内，集群下无法共享配额。

本次修复设计：

- 引入网关侧 `RateLimiter` 抽象，至少提供 `allow(key, policy)` 接口。
- 生产装配改为 Redis 实现，使用 `INCR + EXPIRE` 或等价原子模式维护窗口计数。
- 现有 `InMemoryRateLimiter` 保留为测试或无 Redis 的窄场景实现，但默认自动装配不再使用它。
- `RateLimitWebFilter` 不再依赖具体实现类，而依赖抽象接口。
- 保持现有按“精确 path -> policy”匹配语义，不在本次把限流规则扩展成前缀匹配或 pattern 匹配。

取舍：

- 不做复杂滑动窗口，本次只保证“多副本共享计数”。
- `failOpenOnError` 配置保留，但实现必须把 Redis 异常和无策略区分清楚，不能继续伪装成“edge 已经有基础限流能力”。

## Testing Strategy

### Backend

- `ReindexJobServiceTest`
  - 新增分布式锁获取成功、竞争失败、`finish()` 正确释放的测试。
- `SearchReindexExecutionServiceTest`
  - 验证竞争失败时不会触发 `clearAndReindex`。
- `LoginRateLimitServiceTest`
  - 新增 Redis 正常路径测试。
  - 新增 Redis 抛异常时 fail-closed 的测试。
  - 删除或替换依赖线程池和 timeout 的假设。
- `RateLimitWebFilterTest`
  - 改为基于新的 `RateLimiter` 抽象测试 filter 行为。
- 网关限流实现测试
  - 新增 Redis 实现的单窗口计数测试、窗口过期测试、异常路径测试。

### Frontend

- `http.resolution.test.js`
  - 新增本地 `localhost:12881` / `127.0.0.1:5173` 自动推导到 `:12880` 的断言。
- `imCoreHttp.test.js`
  - 同步补 IM HTTP 自动推导断言。
- 新增或更新 `endpointResolution` / `imRealtimeClient` 相关测试
  - 验证 WS fallback 指向 `:12880/ws/im`，而不是当前页面端口。

## Risks and Mitigations

### Risk: reindex lock TTL 过短

如果 TTL 小于真实执行时长，理论上可能在长时间重建时被第二个实例重新获得。

缓解：

- 先使用保守 TTL。
- 测试和实现预留 renewal 扩展点，但本次不引入复杂续租逻辑。

### Risk: 登录风控改成 fail-closed 后会暴露 Redis 依赖问题

这是预期结果，不是副作用。之前的问题是“依赖坏了却没人知道，还继续放过攻击流量”。

缓解：

- 补 warning 日志和指标。
- 确保错误语义清晰，便于运维识别。

### Risk: 前端本地推导误伤线上

缓解：

- 只在 `localhost` / `127.0.0.1` 且命中指定端口时启用。
- 其它场景保持现有优先级和 same-origin fallback。

### Risk: 网关限流从内存切 Redis 后测试复杂度升高

缓解：

- filter 层测试继续 mock 抽象接口。
- Redis 实现单独测，不把 integration 复杂度硬塞进 filter 单测。

## Implementation Order

1. 先补 `ReindexJobService` 和 `LoginRateLimitService` 的失败测试。
2. 再修前端 endpoint resolution 及其测试。
3. 最后切网关限流抽象和 Redis 实现，避免同时改动两端基础设施。

这个顺序可以先把高风险 correctness / security 问题收口，再处理 edge 能力一致性。
