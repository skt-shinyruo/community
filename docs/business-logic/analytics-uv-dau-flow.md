# UV / DAU 统计链路实现说明

本文说明当前代码里 UV、DAU 的记录与查询是怎么实现的。它属于“业务数据统计”与“平台基础设施”交界处的能力，核心不是复杂控制器，而是 Redis 存储模型与区间计算方式。

## 1. 入口与核心类

### 1.1 HTTP 查询入口

- `backend/community-app/src/main/java/com/nowcoder/community/analytics/controller/AnalyticsController.java`

暴露三个接口：

- `GET /api/analytics/uv`
- `GET /api/analytics/dau`
- `GET /api/analytics/me`

### 1.2 核心服务

- `backend/community-app/src/main/java/com/nowcoder/community/analytics/service/AnalyticsService.java`

### 1.3 存储抽象与 Redis 实现

- `backend/community-app/src/main/java/com/nowcoder/community/analytics/repo/AnalyticsRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/analytics/repo/RedisAnalyticsRepository.java`

当前只保留 Redis 实现。analytics 不再提供本机内存 repository，因为该实现只在单个 JVM 进程内有效，不能作为全局统计存储。

## 2. 对外接口做了什么

### 2.1 查询 UV

接口：

- `GET /api/analytics/uv?start=yyyy-MM-dd&end=yyyy-MM-dd`

controller 只负责把参数解析成 `LocalDate`，再调用：

- `analyticsService.calculateUv(start, end)`

返回值是一个 `Long`。

### 2.2 查询 DAU

接口：

- `GET /api/analytics/dau?start=yyyy-MM-dd&end=yyyy-MM-dd`

同样只是把请求转给：

- `analyticsService.calculateDau(start, end)`

### 2.3 `/me` 的作用

`GET /api/analytics/me` 会返回：

- `CurrentUser.requireJwt(authentication).getSubject()`

它本身不是统计逻辑，而更像一个调试/校验接口，用来确认当前拿到的认证主体是谁。

## 3. 统计服务层做了什么保护

`AnalyticsService` 同时提供：

- `recordUv(date, ip)`
- `recordDau(date, userId)`
- `calculateUv(start, end)`
- `calculateDau(start, end)`

其中查询前都会先跑 `validateRange(start, end)`。

### 3.1 区间不能为空

如果 `start` 或 `end` 是空：

- 抛 `AnalyticsErrorCode.RANGE_INVALID`

### 3.2 结束时间不能早于开始时间

如果：

- `end.isBefore(start)`

直接报错。

### 3.3 查询窗口有上限

代码通过配置项：

- `${analytics.max-days-range:31}`

限制最多查询多少天，默认是 `31` 天。

实际天数计算方式是：

- `ChronoUnit.DAYS.between(start, end) + 1`

也就是首尾都算在内。

这条限制很重要，因为：

- UV 需要做 HyperLogLog 合并
- DAU 需要做位图 OR

如果放开成无限区间，会把 Redis 计算和临时 key 成本拉得很高。

## 4. UV 是怎么存的

### 4.1 每天一个 HyperLogLog key

`RedisAnalyticsRepository.recordUv(...)` 直接执行：

- `opsForHyperLogLog().add("uv:" + date, ip)`

所以 UV 的存储模型是：

- 每天一个 Redis HyperLogLog
- 元素是访问者 IP

这意味着它记录的是“按 IP 去重的独立访问量”，不是按用户 id 去重。

### 4.2 查询区间 UV 时怎么合并

`calculateUv(start, end)` 会：

1. 根据日期范围生成 `uv:2026-04-01` 这类日 key 列表
2. 生成一个临时 union key，前缀是 `uv:tmp:`
3. 用 Lua 脚本执行 `PFMERGE`
4. 给临时 key 设置过期时间
5. 再对 union key 取 `HyperLogLog.size`
6. 最后在 `finally` 里删掉临时 key

这条链路不是逐天相加，而是做“多天并集去重”。

所以它回答的是：

- 在 `start ~ end` 整个区间里，独立 IP 有多少

而不是：

- 每天 UV 的简单求和

### 4.3 为什么 union key 还要 TTL

代码里专门给临时 key 设了 `60s` TTL。

原因也写得很明确：

- 即使 `finally` 删除失败
- 或者中途进程崩溃

临时 key 也应该尽快自动过期，避免 Redis 膨胀。

这是一种很典型的“显式删除 + TTL 双保险”。

## 5. DAU 是怎么存的

### 5.1 每天一个 Bitmap key

`recordDau(date, userId)` 直接执行：

- `setBit("dau:" + date, userId, true)`

所以 DAU 的存储模型是：

- 每天一个 Bitmap
- bit offset 就是 `userId`

哪个用户当天活跃，就把当天位图里对应位置为 `1`。

### 5.2 查询区间 DAU 时怎么合并

`calculateDau(start, end)` 会：

1. 生成区间内所有 `dau:` 日 key
2. 生成一个临时 `dau:tmp:` union key
3. 用 Lua 脚本执行 `BITOP OR`
4. 给临时 key 设置 TTL
5. 对临时 key 做 `BITCOUNT`
6. 最后在 `finally` 中删除临时 key

它的语义同样是区间并集：

- 只要某个用户在区间内任意一天活跃过一次
- 在 union bitmap 里就至少有一个 `1`

因此结果是“区间内活跃用户数”，不是逐天 DAU 相加。

## 6. Redis key 设计

当前关键前缀如下：

- `uv:`
- `dau:`
- `uv:tmp:`
- `dau:tmp:`

日期格式统一是：

- `ISO_LOCAL_DATE`

所以真实 key 看起来像：

- `uv:2026-04-22`
- `dau:2026-04-22`
- `uv:tmp:2026-04-01:2026-04-22:随机串`

临时 key 之所以带随机串，是为了避免并发查询时互相覆盖。

## 7. 记录路径和查询路径的职责分离

### 7.1 记录路径

服务层提供：

- `recordUv(date, ip)`
- `recordDau(date, userId)`

它们负责把“今天访问过”这个事实写进 Redis。

当前对外 controller 没有暴露记录接口，说明记录动作通常发生在别处，比如：

- 登录成功后
- 访问页面时
- 认证过滤器或业务拦截器里

本文聚焦的是统计模块本身，所以只讲当前可见的存储实现，不臆测未展示的接入点。

### 7.2 查询路径

查询路径非常纯：

1. controller 接收日期
2. service 校验范围
3. repository 合并并返回结果

这条路径没有副作用，不会回写数据库或 Redis 主事实。

## 8. 失败路径与边界条件

### 8.1 非法日期范围直接失败

包括：

- 开始或结束为空
- 结束早于开始
- 天数超过上限

### 8.2 空区间 key 列表返回 0

`rangeKeys(...)` 理论上只要范围合法就不会空，但 repository 仍然做了防御性判断：

- `keys.isEmpty()` 时直接返回 `0`

### 8.3 临时 key 删除失败不会影响主结果

`safeDelete(...)` 会吞掉删除异常。

这意味着：

- 查询结果优先返回
- 清理失败交给 TTL 做兜底

这是一个明显偏可用性的取舍。

## 9. 初学者最容易误解的点

### 9.1 UV 不是按用户去重

当前实现用的是 `ip`，所以它本质是：

- 基于 IP 的近似独立访客统计

不是“真实登录用户 UV”。

### 9.2 DAU 也不是访问次数

Bitmap 只记录用户当天是否出现过，重复访问不会重复计数。

### 9.3 区间 UV/DAU 不是逐天加总

这里做的是：

- HyperLogLog 并集
- Bitmap OR 并集

所以区间结果都是“去重后的独立人数”。

### 9.4 统计模块本身不保证埋点来源完整

它只负责存和算，不负责证明“所有应该记录的访问都已经被调用 recordUv/recordDau”。

如果线上数据不对，除了看这里，也要回头查记录调用点是否漏接。

## 10. 与其他模块的关系

### 10.1 与认证/登录的关系

如果系统把登录用户记作活跃用户，那么登录成功或带身份访问时会成为 `recordDau(...)` 的潜在调用点。

### 10.2 与访客访问链路的关系

如果系统要统计 UV，页面访问或请求入口通常会成为 `recordUv(...)` 的潜在调用点。

### 10.3 与后台权限的关系

查询接口属于后台统计面板能力，真正谁能访问这些接口，需要结合对应安全规则类继续看，不是 controller 本身决定的。

## 11. 关键代码定位

- `backend/community-app/src/main/java/com/nowcoder/community/analytics/controller/AnalyticsController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/analytics/service/AnalyticsService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/analytics/repo/AnalyticsRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/analytics/repo/RedisAnalyticsRepository.java`
