# Community Analytics 能力扩展设计稿

**Date:** 2026-04-26
**Status:** Draft for review
**Owner:** Codex

---

## 1. 背景

当前 `community-app` 的 analytics 模块已经具备 Redis 存储和后台查询能力：

- `GET /api/analytics/uv` 查询区间 UV
- `GET /api/analytics/dau` 查询区间 DAU
- `GET /api/analytics/me` 用于确认当前认证主体
- `AnalyticsService` 提供 `recordUv(...)` 和 `recordDau(...)`
- `RedisAnalyticsRepository` 使用 HyperLogLog 存 UV，使用 bitmap 存 DAU
- analytics 存储已经固定为 Redis，不再保留本机内存实现

当前缺口也很明确：

- 没有生产流量接入点调用 `recordUv(...)` 或 `recordDau(...)`
- 没有 PV
- 没有趋势明细接口
- 没有汇总面板接口
- 没有内容维度统计
- 没有留存分析
- Redis 统计 key 还没有完整生命周期和长期归档策略

本设计的目标是把 analytics 从“会算、会查”扩展成“能自动采集、能支撑管理后台看板、能逐步支撑内容与用户留存分析”的模块。

---

## 2. 目标

### 2.1 核心目标

1. 为 UV / DAU 补齐生产采集链路。
2. 增加 PV 统计，支持全局 PV 和受控路径维度 PV。
3. 增加趋势查询接口，返回每日粒度数据。
4. 增加汇总接口，支撑后台 dashboard 的今日、昨日、近 7 天、近 30 天指标展示。
5. 增加后台 analytics dashboard 页面。
6. 为内容维度统计和留存分析设计可演进的数据模型。
7. 为 Redis key 增加命名规范、TTL、快照和长期归档策略。

### 2.2 非目标

本次设计不把 analytics 做成通用埋点平台。

明确不做：

- 任意前端事件 schema 的开放式接收
- 用户行为漏斗、A/B 实验、推荐系统特征仓库
- 实时流处理平台
- Kafka/Flink 等独立分析管道
- 第三方统计系统替代品
- 不受限制的公开 beacon 接口

第一阶段优先服务本项目自己的管理后台和社区核心指标。

---

## 3. 设计原则

### 3.1 采集失败不能影响主业务

analytics 写入必须 fail-open。Redis 短暂不可用、解析失败、路径不在白名单中，都不能让正常业务请求失败。

### 3.2 路径维度必须控基数

PV 和内容统计不能直接把原始 URL 当 key。必须使用归一化路径或业务 id，例如：

- `/api/posts/{postId}`
- `/posts/{postId}`
- `postId`
- `sectionId`

查询参数、分页参数、随机 token、搜索长文本不能直接进入 Redis key。

### 3.3 查询接口继续走后台权限

`/api/analytics/**` 作为后台报表查询面，继续只允许 `ADMIN` 和 `MODERATOR`。

如果后续增加前台采集 endpoint，必须单独命名和单独安全规则，不能混在后台查询面里。

### 3.4 近期用 Redis，长期用快照

Redis 适合保存近期高频计数和集合合并。长期报表应通过每日快照落库，避免 Redis key 无限增长。

### 3.5 分阶段落地

analytics 范围很容易膨胀。实施必须按阶段推进：

1. 先闭环 UV / DAU 自动采集
2. 再补 PV 和趋势/汇总接口
3. 再补前端 dashboard
4. 最后扩展内容统计、留存和归档

---

## 4. 推荐方案

推荐采用“后端请求采集 + Redis 近期聚合 + 后台查询 API + 前端 dashboard”的路线。

### 4.1 为什么不先做公开 beacon

公开 beacon 可以统计更接近页面浏览的前端行为，但第一版会引入更多安全和质量问题：

- 游客可访问，需要限流和反滥用
- 前端可伪造事件，需要严格 schema 和白名单
- 页面路径与 API 路径口径容易混杂
- 对后端当前已有能力帮助不如请求采集直接

因此第一阶段不做公开 `POST /api/analytics/track`。先用服务端 filter/interceptor 采集后端真实请求，保证数据可信、链路简单、风险可控。

### 4.2 为什么不只靠登录记录 DAU

只在登录成功时记录 DAU 会漏掉：

- refresh token 后继续访问的用户
- 已登录状态下第二天直接访问的用户
- 长 session 用户

因此 DAU 应以“带有效身份的成功业务请求”为主，登录成功记录可以作为补充，但不是唯一来源。

---

## 5. 后端采集设计

### 5.1 新增组件

建议在 `backend/community-app/src/main/java/com/nowcoder/community/analytics/ingest/` 下新增以下组件：

- `AnalyticsRequestCaptureFilter`
- `AnalyticsRequestClassifier`
- `AnalyticsClientIpResolver`
- `AnalyticsPrincipalResolver`
- `AnalyticsIngestProperties`
- `AnalyticsIngestService`

职责如下：

`AnalyticsRequestCaptureFilter`

- 作为 `OncePerRequestFilter` 挂在 Spring Web 请求链路中
- 在请求完成后读取 method、path、status、认证主体和客户端 IP
- 只对配置允许的请求调用 `AnalyticsIngestService`
- 捕获并吞掉 analytics 写入异常

`AnalyticsRequestClassifier`

- 判断请求是否计入 UV / DAU / PV
- 排除健康检查、静态资源、后台统计接口、actuator、内部接口
- 对路径做归一化，避免 Redis key 爆炸

`AnalyticsClientIpResolver`

- 解析客户端 IP
- 默认优先使用 `request.getRemoteAddr()`
- 只有配置显式开启可信代理时，才读取 `X-Forwarded-For` 或 `X-Real-IP`
- 对空 IP、非法 IP 返回空结果，不写 UV

`AnalyticsPrincipalResolver`

- 从 `SecurityContext` 或当前 `Authentication` 解析用户 id
- 只接受能转换为正整数的 userId
- 匿名用户不写 DAU

`AnalyticsIngestService`

- 接收归一化后的采集命令
- 调用 `AnalyticsService.recordUv(...)`
- 调用 `AnalyticsService.recordDau(...)`
- Phase 2 增加 PV 后，扩展调用 `AnalyticsService.recordPv(...)`
- 统一记录失败日志，不向调用方抛出异常

### 5.2 采集口径

UV：

- 按自然日记录
- 维度是 IP
- 同一天同一 IP 多次访问只算一个 UV
- 默认只统计成功或业务可见的请求，建议 status `< 500`

DAU：

- 按自然日记录
- 维度是整数 userId
- 同一天同一用户多次访问只算一个 DAU
- 只统计已认证用户
- 默认只统计 status `< 500`

PV：

- 按自然日记录
- 每个符合规则的请求计一次
- 可以同时写全局 PV 和路径维度 PV

### 5.3 初始采集范围

第一版建议采集：

- `/api/posts/**`
- `/api/search/**`
- `/api/messages/**`
- `/api/notices/**`
- `/api/im-governance/**` 中的后台可见请求

第一版建议排除：

- `/api/analytics/**`
- `/api/auth/**`，登录成功 DAU 可通过 auth service 显式补记
- `/api/ops/**`
- `/actuator/**`
- `/internal/**`
- `/files/**`

排除 `/api/auth/**` 的原因是登录链路包含敏感语义和失败请求，第一版不把登录风控与访问统计混在一起。登录成功补记 DAU 可以在 `AuthService.login(...)` 成功发 token 后显式调用 analytics。

### 5.4 配置

建议新增配置。下面是显式开启采集时的示例配置：

```yaml
analytics:
  max-days-range: 31
  ingest:
    enabled: true
    record-uv: true
    record-dau: true
    record-pv: true
    trusted-proxy-headers-enabled: false
    include-paths:
      - /api/posts/**
      - /api/search/**
      - /api/messages/**
      - /api/notices/**
    exclude-paths:
      - /api/analytics/**
      - /api/auth/**
      - /api/ops/**
      - /actuator/**
      - /internal/**
      - /files/**
```

默认应保持保守：

- `ingest.enabled` 可以默认 `false`，上线时显式打开
- 本地和测试可以打开，以便验证 dashboard
- 可信代理头默认关闭

---

## 6. Redis 数据模型

### 6.1 Key 前缀

当前 key 使用：

- `uv:{date}`
- `dau:{date}`
- `uv:tmp:{start}:{end}:{uuid}`
- `dau:tmp:{start}:{end}:{uuid}`

扩展后建议统一迁移到版本化前缀：

- `analytics:v1:uv:{date}`
- `analytics:v1:dau:{date}`
- `analytics:v1:pv:{date}`
- `analytics:v1:pv:path:{date}`
- `analytics:v1:post:pv:{date}`
- `analytics:v1:post:uv:{postId}:{date}`
- `analytics:v1:search:keyword:{date}`
- `analytics:v1:tmp:uv:{start}:{end}:{uuid}`
- `analytics:v1:tmp:dau:{start}:{end}:{uuid}`

为了降低迁移风险，第一阶段可以继续兼容旧 key，新增能力使用新前缀。后续单独做 key 迁移或历史清理。

### 6.2 UV

数据结构：

- Redis HyperLogLog
- key: `analytics:v1:uv:{date}`
- value: IP

查询区间：

- 对日 key 做 `PFMERGE`
- 临时 key 设 60 秒 TTL
- 读取 `PFCOUNT`
- finally 删除临时 key

### 6.3 DAU

数据结构：

- Redis bitmap
- key: `analytics:v1:dau:{date}`
- bit offset: userId

查询区间：

- 对日 key 做 `BITOP OR`
- 临时 key 设 60 秒 TTL
- 读取 `BITCOUNT`
- finally 删除临时 key

### 6.4 PV

全局 PV：

- Redis string counter
- key: `analytics:v1:pv:{date}`
- 写入使用 `INCR`

路径 PV：

- Redis sorted set
- key: `analytics:v1:pv:path:{date}`
- member: 归一化路径
- score: 访问次数
- 写入使用 `ZINCRBY`

路径维度必须只写归一化后的路径模板，不写原始 URL。

### 6.5 内容维度

帖子 PV：

- Redis sorted set
- key: `analytics:v1:post:pv:{date}`
- member: postId
- score: 访问次数

帖子 UV：

- Redis HyperLogLog
- key: `analytics:v1:post:uv:{postId}:{date}`
- value: 访客标识。已认证用户使用 `u:{userId}`，匿名访问使用 `ip:{normalizedIp}`。同一次访问同时具备用户 id 和 IP 时优先使用 `u:{userId}`，避免同一用户跨 IP 被重复计入帖子 UV。

搜索关键词：

- Redis sorted set
- key: `analytics:v1:search:keyword:{date}`
- member: 规范化关键词
- score: 搜索次数

关键词必须做长度限制和规范化：

- trim
- lower-case
- 最大长度限制
- 空字符串不记录
- 明显敏感或过长输入不记录

### 6.6 留存

留存分析需要两个基础集合：

- 新用户 cohort
- 每日活跃用户

推荐模型：

- `analytics:v1:new-user:{date}` bitmap，记录当天注册用户
- `analytics:v1:dau:{date}` bitmap，记录当天活跃用户

次日留存：

- `BITOP AND` 新用户日 bitmap 和次日 DAU bitmap
- `BITCOUNT` 得到留存人数

7 日留存：

- `BITOP AND` 新用户日 bitmap 和第 7 天 DAU bitmap

留存功能依赖注册成功事件或用户模块显式调用 analytics。它不应在第一阶段实现。

---

## 7. 查询 API 设计

### 7.1 保留现有接口

继续保留：

- `GET /api/analytics/uv`
- `GET /api/analytics/dau`
- `GET /api/analytics/me`

`/me` 可继续保留作为后台鉴权诊断接口，但不应出现在 dashboard 主流程里。

### 7.2 新增 PV 查询

```http
GET /api/analytics/pv?start=2026-04-01&end=2026-04-07
```

返回：

```json
{
  "code": 0,
  "message": "ok",
  "data": 12345
}
```

### 7.3 新增趋势接口

推荐使用统一趋势接口，避免为每个指标创建大量 endpoint：

```http
GET /api/analytics/trends?metrics=uv,dau,pv&start=2026-04-01&end=2026-04-07
```

规则：

- `metrics` 只接受 `uv`、`dau`、`pv`
- `metrics` 为空时默认返回 `uv,dau,pv`
- 任一未知 metric 返回范围参数错误
- 日期范围继续受 `analytics.max-days-range` 限制

返回：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "start": "2026-04-01",
    "end": "2026-04-07",
    "series": [
      {
        "metric": "uv",
        "points": [
          { "date": "2026-04-01", "value": 120 },
          { "date": "2026-04-02", "value": 145 }
        ]
      },
      {
        "metric": "dau",
        "points": [
          { "date": "2026-04-01", "value": 80 },
          { "date": "2026-04-02", "value": 91 }
        ]
      }
    ]
  }
}
```

### 7.4 新增汇总接口

```http
GET /api/analytics/summary?range=7d
```

支持 range：

- `today`
- `yesterday`
- `7d`
- `30d`

环比周期定义：

- `today` 对比昨天
- `yesterday` 对比前天
- `7d` 对比紧邻的前 7 天
- `30d` 对比紧邻的前 30 天
- 当 previous 为 0 且 current 大于 0 时，`changePercent` 返回 `null`，避免制造无意义的无限增长百分比

返回：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "range": "7d",
    "cards": [
      {
        "metric": "uv",
        "current": 1024,
        "previous": 980,
        "changePercent": 4.49
      },
      {
        "metric": "dau",
        "current": 512,
        "previous": 500,
        "changePercent": 2.40
      },
      {
        "metric": "pv",
        "current": 12000,
        "previous": 10800,
        "changePercent": 11.11
      }
    ]
  }
}
```

### 7.5 新增热门维度接口

路径排行：

```http
GET /api/analytics/top/paths?date=2026-04-26&limit=20
```

帖子排行：

```http
GET /api/analytics/top/posts?date=2026-04-26&limit=20
```

搜索关键词排行：

```http
GET /api/analytics/top/search-keywords?date=2026-04-26&limit=20
```

这些接口应限制 `limit` 最大值，例如最大 100。

### 7.6 留存接口

留存接口后置到留存阶段：

```http
GET /api/analytics/retention?cohortStart=2026-04-01&cohortEnd=2026-04-07&days=1,7
```

返回按 cohort date 分组的留存率。

---

## 8. 后台 Dashboard 设计

### 8.1 页面目标

后台 analytics dashboard 第一版应服务管理员快速判断：

- 今天访问是否正常
- 最近一周 UV / DAU / PV 是否上升或下降
- 哪些页面或内容最热
- 是否存在采集异常，例如 PV 有值但 UV/DAU 长期为 0

### 8.2 页面组成

建议页面包含：

1. 日期范围选择器
2. 指标卡片
   - UV
   - DAU
   - PV
   - DAU/UV 比例
3. 趋势折线图
   - UV
   - DAU
   - PV
4. 热门路径表格
5. 热门帖子表格
6. 热门搜索词表格
7. 数据口径说明入口

### 8.3 前端 API 封装

当前已有 `frontend/src/api/services/analyticsService.js`，应扩展而不是新建平行服务。

建议新增方法：

- `pv({ start, end })`
- `trends({ metrics, start, end })`
- `summary({ range })`
- `topPaths({ date, limit })`
- `topPosts({ date, limit })`
- `topSearchKeywords({ date, limit })`

### 8.4 前端交互边界

第一版 dashboard 不提供：

- 自定义埋点事件配置
- 任意维度下钻
- 数据导出
- 报警规则配置

这些能力可以后续独立设计。

---

## 9. Redis 生命周期与长期归档

### 9.1 TTL 策略

建议配置：

```yaml
analytics:
  retention:
    raw-days: 90
    top-dimension-days: 90
    temp-key-ttl: 60s
```

适用规则：

- UV / DAU / PV 日 key 默认保留 90 天
- 路径、帖子、搜索词排行默认保留 90 天
- 临时 union key 继续保留 60 秒 TTL

### 9.2 每日快照

当 dashboard 需要查看 90 天以上历史时，不应继续依赖 Redis 原始 key。

建议新增数据库表：

- `analytics_daily_metric_snapshot`
- `analytics_top_dimension_snapshot`
- `analytics_retention_snapshot`

快照内容：

- date
- metric
- value
- dimension_type
- dimension_key
- rank
- created_at

### 9.3 快照任务

快照任务可以后续接入已有任务体系。

建议行为：

- 每天凌晨汇总前一天数据
- 写入 DB 快照
- 对 Redis 原始 key 设置或刷新 TTL
- 失败时记录日志，不删除 Redis 原始 key

第一版不强制实现快照任务，但 Redis key 命名和查询服务应为快照预留接口。

---

## 10. 错误处理与降级

### 10.1 写入降级

采集写入失败时：

- 记录 warn 日志
- 带上 metric、path、date、traceId
- 不影响业务响应

### 10.2 查询失败

后台查询失败时：

- Redis 连接不可用返回 `AnalyticsErrorCode.COUNTER_UNAVAILABLE`
- 非预期异常返回 `AnalyticsErrorCode.INTERNAL_ERROR`
- 日期范围非法返回 `AnalyticsErrorCode.RANGE_INVALID`

当前错误码已经存在，可继续复用：

- `COUNTER_UNAVAILABLE`
- `INTERNAL_ERROR`
- `RANGE_INVALID`

### 10.3 数据缺失

某天没有 key 时返回 0，不视为错误。

---

## 11. 安全设计

### 11.1 查询面

后台查询接口继续沿用：

- `/api/analytics/**`
- `ADMIN` 或 `MODERATOR`

### 11.2 采集面

第一阶段不新增公开采集 endpoint。

如果后续增加前端 beacon，应使用独立路径，例如：

- `POST /api/analytics-events`

并且必须具备：

- 严格 JSON schema
- 限流
- 路径白名单
- payload 大小限制
- 不接受任意 userId
- 不接受任意 IP
- 匿名请求只能写 PV/UV，不能写 DAU

---

## 12. 测试策略

### 12.1 后端单元测试

需要覆盖：

- `AnalyticsClientIpResolver`
- `AnalyticsPrincipalResolver`
- `AnalyticsRequestClassifier`
- `AnalyticsIngestService`
- `RedisAnalyticsRepository` 新增 PV、排行、趋势能力
- `AnalyticsService` 新增区间、委托、错误转换逻辑
- `AnalyticsController` 新接口

### 12.2 集成测试

需要覆盖：

- filter 在成功请求后触发采集
- filter 对排除路径不采集
- analytics 写失败不影响业务请求
- ADMIN/MODERATOR 可查 analytics
- 普通用户不能查 analytics

### 12.3 前端测试

需要覆盖：

- API service 参数拼装
- dashboard loading 状态
- 空数据状态
- 日期范围切换
- 趋势图数据映射

---

## 13. 分期计划

### Phase 1：UV / DAU 自动采集闭环

目标：

- 真实请求能自动写入 UV / DAU
- Redis 写失败不影响业务
- 采集范围可配置
- 登录成功后可补记当日 DAU，但 DAU 主口径仍以已认证业务请求为准

产出：

- `AnalyticsRequestCaptureFilter`
- IP 解析器
- 用户解析器
- 路径分类器
- 采集配置
- `AuthService.login(...)` 成功后的 DAU 补记接线
- 写入失败日志
- resolver、classifier、filter、ingest service 和 Redis repository 单元测试

### Phase 2：PV、趋势与汇总 API

目标：

- 支持 PV
- 支持日粒度趋势
- 支持 dashboard summary

产出：

- Redis PV counter
- 路径 PV sorted set
- `/api/analytics/pv`
- `/api/analytics/trends`
- `/api/analytics/summary`
- DTO 和测试

### Phase 3：后台 Dashboard

目标：

- 管理员能在前端看到基础统计看板

产出：

- 前端 analytics service 扩展
- dashboard 页面
- 指标卡片
- 趋势图
- 热门路径表
- 空数据和错误状态

### Phase 4：内容维度统计

目标：

- 支持热门帖子和热门搜索词

产出：

- 帖子 PV
- 帖子 UV
- 搜索关键词计数
- top posts API
- top search keywords API
- dashboard 表格扩展

### Phase 5：留存与长期归档

目标：

- 支持新用户留存分析
- 支持 90 天以上历史查询

产出：

- 新用户 cohort 记录
- retention 查询 API
- 每日快照表
- 快照任务
- Redis TTL 策略
- 快照查询回退逻辑

---

## 14. 验收标准

### 14.1 Phase 1 验收

- 访问被允许采集的接口后，当日 UV 有增长
- 已登录用户访问被允许采集的接口后，当日 DAU 有增长
- 排除路径不会写入 analytics
- Redis 写失败不会导致业务接口失败
- analytics 查询接口仍只允许 ADMIN/MODERATOR

### 14.2 Phase 2 验收

- PV 查询返回区间总数
- trend 接口返回每日 UV / DAU / PV
- summary 接口返回今日、昨日、近 7 天或近 30 天聚合指标
- 查询区间超过 `analytics.max-days-range` 时返回范围错误

### 14.3 Phase 3 验收

- 后台页面能展示 UV / DAU / PV 卡片
- 后台页面能展示趋势图
- 后台页面能展示热门路径
- 空数据时页面有明确空状态
- 接口失败时页面有明确错误状态

### 14.4 Phase 4 验收

- 访问帖子详情后，帖子 PV 排行可见
- 搜索关键词后，关键词排行可见
- 原始 URL 和超长关键词不会进入 Redis key

### 14.5 Phase 5 验收

- 能查询新用户次日留存和 7 日留存
- Redis 原始 key 有 TTL
- 每日快照能落库
- 超出 Redis 保留窗口的历史查询能走快照

---

## 15. 风险与控制

### 15.1 Redis 写入放大

风险：

- 开启 UV、DAU、PV 和路径 PV 后，每个被采集请求会写多个 key

控制：

- 只采集白名单路径
- 排除高频无价值接口
- 后续可加采样或异步缓冲

### 15.2 路径基数爆炸

风险：

- 原始 URL、查询参数、动态 id 直接作为 key 会撑爆 Redis

控制：

- 只写归一化路径
- 不记录 query string
- 限制 top 维度数量

### 15.3 DAU userId offset 过大

风险：

- bitmap 使用 userId 作为 offset，如果 userId 过大，会导致 Redis bitmap 变大

控制：

- 只接受正整数用户 id
- 监控最大 userId
- 如果未来 userId 不适合 bitmap，单独设计用户 id 压缩映射

### 15.4 指标口径混乱

风险：

- API 请求 PV、页面 PV、帖子 PV 混在一起

控制：

- 明确 metric 和 dimension type
- dashboard 上展示口径说明
- Redis key 和 DTO 中区分全局、路径、内容维度

---

## 16. 推荐结论

本仓库 analytics 的下一步应先补“真实采集闭环”，再扩展展示和分析能力。

推荐实施顺序：

1. Phase 1：UV / DAU 自动采集闭环
2. Phase 2：PV、趋势与汇总 API
3. Phase 3：后台 Dashboard
4. Phase 4：内容维度统计
5. Phase 5：留存与长期归档

这条路线能先解决当前最大问题：已有 Redis 统计能力但没有生产调用方。等数据能稳定进入系统后，再做趋势、汇总、前端页面和更复杂的内容/留存分析，风险最低，也最符合当前代码结构。
