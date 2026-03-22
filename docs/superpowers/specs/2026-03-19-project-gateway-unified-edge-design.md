# Project Gateway Unified Edge Design

日期：2026-03-19  
主题：统一项目 Gateway，收口 `community-app`、`im-core`、`im-realtime` 的对外入口，并演进为真正的 IM 接入层

## 1. 背景

当前项目的对外入口存在三个明显边界：

- `community-app`：主站 HTTP 业务入口
- `im-core`：IM HTTP 读接口与部分内部接口
- `im-realtime`：IM WebSocket 长连接入口

现状可以工作，但会带来以下问题：

1. 项目级入口分散，前端与客户端需要直接面向多个后端服务和端口
2. HTTP 与 IM 接入层的鉴权、限流、灰度、观测策略分散在不同服务边界
3. `im-realtime` 当前在 WebSocket 建立后的第一帧 `auth` 才拿到用户身份，这使常规 L7 负载均衡只能做基于 IP/cookie 的弱粘性，无法按 `userId` 做稳定分片
4. IM 长连接、HTTP 读接口、治理能力和灰度发布没有统一的 edge 策略面
5. `im-realtime` 目前既是公网接入点，又是 IM 实时 worker，职责耦合较重

本设计的目标，是把这些入口收口到一个统一的 `community-gateway`，并逐步把它演进成真正的项目级边界层与 IM 接入层。

---

## 2. 目标

### 2.1 核心目标

1. 提供统一的项目级入口，前端和客户端未来只认 `community-gateway`
2. 将 `community-app`、`im-core` 的 HTTP 对外接口纳入统一 gateway
3. 将 `im-realtime` 的 WebSocket 接入纳入统一 gateway
4. 让 gateway 终止外部 WebSocket，读取第一帧 `auth`，完成 JWT 校验并提取 `userId`
5. 让 gateway 基于 `userId` / shard 做稳定路由，而不是依赖传统 sticky session
6. 把鉴权、限流、灰度、trace、审计、统一入口策略上移到 gateway
7. 将 `im-realtime` 从“公网接入点”演进为“内部 IM session worker”
8. 保持 `im-core` 作为 IM 权威状态 owner，不把持久化与历史职责搬到 gateway

### 2.2 阶段性目标

虽然长期目标全部保留，但实施上按阶段推进：

- 第一阶段完成统一项目入口与基础治理面
- 第二阶段接管 IM WebSocket 会话入口与首帧鉴权
- 第三阶段完成 `userId/shard` 稳定路由与内部 bridge
- 第四阶段完善灰度、热点治理、多池路由与长期运维能力

### 2.3 非目标

本设计不包括以下事项：

- 不把 `community-app` 或 `im-core` 的业务实现并入 gateway
- 不让 gateway 成为 IM 权威状态存储
- 不在第一阶段重写 `im-realtime` 的现有私信/群聊业务协议
- 不在第一阶段实现跨机房连接迁移或实时连接热迁移
- 不把 Kafka backplane 替换为其他消息系统

---

## 3. 约束与现有实现事实

### 3.1 `im-realtime` 的当前角色

当前 `im-realtime`：

- 不持有数据库型持久化状态
- 维护本机在线连接、用户到连接映射、房间本地索引、出站缓冲等进程内软状态
- 鉴权在 WebSocket 建立后的第一帧 `auth` 完成，而不是在 HTTP upgrade 阶段完成

这意味着：

- 它不是“完全无状态”服务
- 但它也不是持久化 owner
- 常规 L7 网关无法在握手前按 `userId` 做精确路由

### 3.2 `im-core` 的当前角色

当前 `im-core` 是 IM 权威状态 owner，负责：

- 私信/群聊消息落库
- 顺序号分配
- 幂等
- 历史查询
- 未读状态
- 房间成员关系

这一定义在新架构下保持不变。

### 3.3 IM 正确性模型

当前 IM 的正确性依赖：

- WebSocket best-effort 推送
- 客户端断线重连后通过 `im-core` HTTP 做 history/backfill

这意味着新 gateway 不能把正确性建立在“连接必须回到同一实例”上。

---

## 4. 可选方案

### 方案 A：标准网关 + 常规负载均衡

做一个普通项目 gateway，仅处理：

- HTTP 反向代理
- TLS
- 基础限流
- WebSocket 转发
- 可选 sticky session

不读取首帧 `auth`，不做用户级 shard 路由。

优点：

- 改动小
- 落地快
- 可先统一入口

缺点：

- 无法按 `userId` 做稳定路由
- sticky 仍停留在 IP/cookie 粘性
- 不能真正把 IM 接入能力收口到 gateway

### 方案 B：HTTP gateway + 独立 IM gateway

HTTP 流量与 IM 长连接分别进入不同网关。

优点：

- 风险隔离
- 便于针对 IM 优化连接治理

缺点：

- 不是单一项目入口
- 平台治理面会分裂
- 不符合“全项目统一 gateway”的目标

### 方案 C：统一 `community-gateway` + IM 接入层能力

新增一个独立的 `community-gateway`，同时承担：

- 项目统一 HTTP 入口
- IM WebSocket 入口
- 首帧 `auth` 校验
- `userId/shard` 稳定路由
- 内部 bridge 到 `im-realtime` worker

优点：

- 同时满足统一入口与 IM 接入收口
- 能摆脱传统 sticky 的能力上限
- 后续可承接灰度、热点治理、多池路由

缺点：

- 复杂度最高
- 需要定义新的 gateway 与 worker 间内部协议
- rollout 风险显著高于普通反向代理

### 推荐

推荐采用 **方案 C**。

原因：

- 这是唯一一个同时满足“项目级统一入口”和“真正的 IM 接入层演进”两个目标的方案
- 只要通过 phased rollout 控制风险，它的长期收益高于方案 A/B
- 通过“gateway 终止外部 WS，但第一阶段不重写 IM 业务协议”的策略，可以把复杂度压在可控范围内

---

## 5. 目标架构

### 5.1 顶层边界

目标架构下，对外只暴露一个统一入口：

- `community-gateway`

对外客户端与前端只面对：

- 一个 HTTP 域名
- 一个 WebSocket 路径

后端内部结构变为：

- `community-gateway`
  - HTTP edge
  - WS edge
  - shard router
  - policy plane
  - bridge manager
- `community-app`
  - 主站 HTTP 业务服务
- `im-core`
  - IM 权威状态服务
- `im-realtime`
  - 内部 IM 实时 worker

### 5.2 HTTP 流量

HTTP 流量全部先进入 `community-gateway`：

- 主站 API -> 转发到 `community-app`
- IM HTTP 读接口 -> 转发到 `im-core`

gateway 负责：

- 鉴权入口收口
- trace 与日志
- 限流
- 灰度路由
- 统一错误收敛
- 访问审计

### 5.3 WebSocket 流量

WebSocket 连接也全部先进入 `community-gateway`：

1. gateway 接住外部 `/ws/im`
2. 终止外部 WebSocket
3. 等待并解析第一帧 `auth`
4. 校验 JWT，提取 `userId`
5. 交给 shard router 选择目标 `im-realtime` worker
6. gateway 与目标 worker 建立内部 bridge
7. 后续客户端帧与 worker 推送都经 gateway 透传

### 5.4 `im-realtime` 的新角色

演进后，`im-realtime` 不再是公网接入点，而是内部 IM session worker。

它保留：

- 现有私信/群聊实时协议处理
- Kafka command 生产
- Kafka event 消费
- 在线连接状态
- 房间本地索引
- 推送与 coalescing 逻辑

它失去：

- 公网直连入口职责
- 外部 edge 鉴权职责
- 外部治理入口职责

---

## 6. 核心组件拆分

### 6.1 HTTP Edge

职责：

- 对外统一 HTTP 入口
- 路由到 `community-app` / `im-core`
- 统一 request id / traceId
- 统一 access log / metrics
- 统一限流与灰度入口

边界：

- 不实现业务逻辑
- 不改写领域语义
- 只做 edge 级治理与转发

### 6.2 WS Edge

职责：

- 对外唯一 `/ws/im`
- WebSocket 握手与生命周期管理
- 首帧 `auth` 状态机
- 建立“未认证连接 -> 已认证连接”的连接状态迁移

边界：

- 只负责接入层协议，不直接承担 IM 业务语义

### 6.3 Shard Router

职责：

- 按 `userId` 做稳定 shard 选择
- 支持一致性哈希与配置驱动路由
- 支持灰度 shard、版本池与热点隔离

第一阶段实现建议：

- 一致性哈希 + worker 列表

后续演进建议：

- 基于配置的用户/群组路由
- 热点用户搬迁
- 按版本 worker 池切流

### 6.4 Bridge Manager

职责：

- 为外部客户端连接选择目标 worker
- 建立 gateway <-> worker 的内部 bridge
- 管理双向消息转发
- 处理 bridge 失败、worker 失联、优雅摘流

边界：

- 第一阶段不重写 IM 业务语义
- 优先桥接现有 worker 协议

### 6.5 Policy Plane

职责：

- 鉴权策略
- HTTP / WS 限流策略
- 灰度配置
- 服务发现与 worker 健康视图
- 发布开关与降级开关

---

## 7. 关键数据流

### 7.1 HTTP

```text
Client -> community-gateway -> community-app / im-core
```

### 7.2 WebSocket

```text
Client -> community-gateway
       -> 首帧 auth
       -> JWT 校验 + userId 提取
       -> shard router 选 worker
       -> bridge 到选中的 im-realtime worker
       -> 后续双向透传
```

### 7.3 IM 持久化

```text
Client -> gateway -> chosen worker
       -> Kafka command
       -> im-core consume + persist
       -> Kafka event
       -> workers consume and fanout to local sessions
       -> gateway 将本 worker 推送回给外部客户端
```

### 7.4 群聊更新

群聊保持当前模型不变：

- worker 收到 `roomUpdatedBatch`
- 客户端再通过 `im-core` HTTP 按 `seq` 拉正文

---

## 8. 路由与 shard 策略

### 8.1 为什么不用传统 sticky 作为长期方案

传统 sticky session 只能做到：

- IP affinity
- cookie affinity

它们都不能稳定表达：

- 同一 `userId`
- 多设备 / 多 NAT / 多浏览器
- 版本灰度 / shard 分流

因此，本方案里传统 sticky 最多只能是“弱优化”，不能作为正确性或长期演进的基础。

### 8.2 稳定路由原则

稳定路由以 `userId` 为主键：

- 相同 `userId` 在 worker 集合稳定时应优先落到同一 shard
- worker 集合变化时，尽量只迁移最少量用户

推荐第一阶段使用：

- 一致性哈希

推荐后续支持：

- 用户分组强制绑定
- 热点用户隔离
- 版本池切流
- tenant / region 扩展键

### 8.3 连接与用户的关系

本设计按“用户”而不是“单连接”做路由，是为了：

- 让多连接用户更稳定地落到同一 worker
- 减少重连漂移
- 降低跨 worker 状态分裂

---

## 9. 安全与鉴权

### 9.1 HTTP

HTTP 鉴权入口上移到 gateway：

- JWT 校验
- 路径级策略分流
- 上游身份透传

领域服务仍保留必要的域内权限判断与业务级校验，不完全信任 gateway。

### 9.2 WebSocket

gateway 负责：

- 读取首帧 `auth`
- 校验 JWT
- 提取 `userId`
- 决定目标 shard

worker 不再对外暴露公网 WS 入口，但可保留最小内部校验能力，避免内部协议误用。

### 9.3 治理

长期目标是把跨服务的一致治理能力收口到 gateway：

- 速率限制
- 黑白名单
- 灰度与版本切流
- 风险拦截

但领域业务规则，例如私信能否发送、目标用户是否合法等，仍保持在业务服务 owner 侧。

---

## 10. 失败处理与降级

### 10.1 HTTP

gateway 对 HTTP 错误应尽量透明：

- 不改变主要业务语义
- 只做 trace、格式统一和安全裁剪

### 10.2 WebSocket

WS 失败分层处理：

- 握手失败：直接拒绝
- 首帧 `auth` 失败：返回协议级 `auth_error`
- shard 选择失败：返回明确系统错误并关闭连接
- bridge 建立失败：快速失败，不留悬空连接
- worker 失联：关闭连接，让客户端重连并 backfill

### 10.3 降级开关

必须支持以下开关：

1. HTTP 仅透明反代
2. WS 仅统一入口 + 透明转发
3. WS 终止外部连接，但不启用 `userId/shard`
4. WS 启用完整 `userId/shard` 路由

---

## 11. 演进阶段

### Phase 1：统一项目入口

- 新建 `community-gateway`
- 收口 `community-app` / `im-core` HTTP
- 对外统一入口
- IM WebSocket 先透明转发
- 收口基础 trace / 限流 / 灰度开关

### Phase 2：接管外部 IM WebSocket

- gateway 终止外部 WS
- 读取首帧 `auth`
- 校验 JWT 与提取 `userId`
- 建立外部 WS 生命周期管理

### Phase 3：引入 shard router 与 bridge

- 根据 `userId` 选 worker
- 建立 gateway 到 worker 的内部 bridge
- `im-realtime` 从公网接入层降级为内部 worker

### Phase 4：完善治理与长期能力

- 完成统一 HTTP / WS 限流收口
- 完成灰度切流
- 支持热点隔离、版本 worker 池、多池路由

---

## 12. 实施任务拆分原则

后续 implementation plan 应按以下任务组拆分：

- A：基础骨架与统一入口
- B：HTTP 治理面收口
- C：IM WebSocket 接入层改造
- D：Shard Router 与内部 bridge
- E：`im-realtime` 角色收缩为内部 worker
- F：观测、运维与发布能力
- G：前端与调用方切换

其中适合并行子代理推进的任务主要包括：

- HTTP 路由
- trace / log / metrics
- HTTP 限流框架
- 灰度策略
- WS 级连接治理
- shard router
- worker 健康视图
- observability / dashboard / alerts
- 前端 HTTP / WS 切换

不适合并行、应由主线程或单一负责人控制的任务主要包括：

- gateway 服务骨架
- WS 首帧 `auth` 状态机
- bridge 协议
- worker 内部入口适配

---

## 13. 测试与验证要求

### 13.1 HTTP

- `community-app` 现有主要 HTTP 路由经 gateway 后行为不变
- `im-core` 读接口经 gateway 后行为不变
- traceId、错误 envelope、限流命中、灰度命中可观测

### 13.2 WebSocket

- gateway 可成功接住 `/ws/im`
- 首帧 `auth` 校验通过时，连接可稳定路由到目标 worker
- 认证失败、worker 不可用、bridge 失败时错误清晰且可重连
- 私信链路在 gateway 下仍可正常工作
- 群聊链路在 gateway 下仍可正常工作
- history/backfill 仍然正确

### 13.3 Rollout

- 旧入口与新入口可并存
- 可按用户或流量比例灰度
- 可一键回到透明转发
- 必要时可切回旧直连模式

---

## 14. 风险

### 风险 1：范围过大

统一项目 gateway + IM 接入层能力是一个跨 HTTP、长连接、路由和发布的联合项目。

缓解：

- 强制 phased rollout
- 用清晰的 task group 做分治
- 保持 gateway 与 worker 的职责边界

### 风险 2：bridge 复杂度高

内部 bridge 是本方案最难的技术点之一。

缓解：

- 第一阶段优先桥接现有协议，不立刻重写 worker 协议
- 把 bridge 失败设计成快速失败 + 重连/backfill，而不是隐式重试黑盒

### 风险 3：错误依赖 sticky 语义

如果实现依然依赖老式 sticky session，而不是显式 shard 路由，长期会被 NAT、重连漂移、灰度需求击穿。

缓解：

- 在设计和测试里明确：sticky 只做弱优化，不做正确性前提

### 风险 4：gateway 成为过重单点

如果把太多业务规则塞进 gateway，会让它变成新的单点复杂系统。

缓解：

- gateway 只承担 edge 和接入层责任
- 业务 owner 仍在 `community-app` 与 `im-core`

---

## 15. 验收标准

满足以下条件时，本设计可视为成功落地：

1. 前端和客户端只需面对统一项目入口
2. `community-app`、`im-core` 的对外 HTTP 由 gateway 统一收口
3. IM WebSocket 由 gateway 统一收口
4. gateway 可基于首帧 `auth` 提取 `userId` 并选择 worker
5. `im-realtime` 成为内部 worker，而不是公网接入点
6. 私信、群聊、history、backfill 在新入口下行为正确
7. 限流、灰度、trace、审计在 gateway 可观测
8. 整个 rollout 可灰度、可降级、可回滚

---

## 16. 结论

推荐新增独立的 `community-gateway`，将其建设为本项目统一 edge 与 IM 接入层：

- 对 HTTP：统一收口项目级入口与治理策略
- 对 IM：终止外部 WS、做首帧鉴权、按 `userId/shard` 路由到内部 `im-realtime` worker
- 对权威状态：继续由 `im-core` 持有

这是一个高复杂度但高长期收益的演进方向。只要采用 phased rollout、明确边界、保持强回滚能力，就值得作为本项目的长期主路线。
