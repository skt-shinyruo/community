# 私信单一 Owner 收敛与旧链路下线（设计稿）

日期：2026-03-30  
主题：私信能力单一 owner 化 + legacy `/api/messages/**` 硬下线 + IM 成为唯一事实来源

## 1. 背景与问题

当前仓库中的“私信”能力处于双轨并存状态：

- 前端 `/messages` 页面实际走 IM 链路：
  - HTTP：`/api/im/**`（`im-core`）
  - WebSocket：`/ws/im`（`im-realtime`）
- `community-app` 仍保留完整的 legacy 站内私信 API：`/api/messages/**`
- legacy `/api/messages/**` 会直接读写 `community-app` 的 `message` 表
- IM 链路则写入 `im-core` 的 `im_conversation` / `im_private_message` / `im_conversation_read_state`

这不是简单的“两个入口指向同一能力”，而是两套不同的数据模型、协议语义和代码 owner：

- legacy HTTP 私信：
  - 同步请求/响应模型
  - 前端依赖 `Idempotency-Key`
  - 已读语义是“按 message id 列表标记”
- IM 私信：
  - WebSocket + Kafka 异步接单模型
  - 幂等依赖 `clientMsgId`
  - 已读语义是“推进 conversation 的 `lastReadSeq`”

结果是：

1. “私信能力”没有 capability-level single owner  
2. 后续新增需求很容易继续在 legacy `/api/messages/**` 上堆补丁  
3. 代码结构会持续误导开发者认为 `community-app` 仍拥有私信读写  
4. 即使前端主页面已切到 IM，仓库里仍保留完整旧系统，形成长期架构债

本设计的目标不是“再做一层兼容壳”，而是明确终态 owner，并删除旧链路。

## 2. 目标与非目标

### 2.1 目标

1) 让 `community-im` 成为私信能力的唯一 owner  
2) 删除 `community-app` 中 legacy 私信读写能力与 `/api/messages/**` 路由  
3) 删除前端针对 legacy 私信的 API 客户端与幂等特判  
4) 保留并明确 `community-app` 在“私信治理规则”上的职责边界  
5) 让重新部署后的系统只存在一套私信事实来源、一套读模型、一套写入口

### 2.2 非目标

- 不兼容旧客户端
- 不保留、不迁移 legacy 私信历史数据
- 不做双写、回填、灰度读切换
- 不为 `/api/messages/**` 提供 façade、转发层或别名接口
- 不在本轮重做 IM 协议，只在现有 IM 能力基础上完成 owner 收敛

### 2.3 前置条件

本方案依赖以下已明确约束：

- 可以重新部署
- 无需保留旧私信历史数据
- 无需兼容 legacy `/api/messages/**` 客户端

这些前置条件成立后，最佳路径不是“迁移兼容”，而是“硬切换 + 删除旧链路”。

## 3. 方案选型

本轮评估过以下三种方向：

### 3.1 方案 A：`community-im` 成为私信唯一 owner（推荐）

- `im-realtime` 作为私信实时写入口
- `im-core` 作为私信权威存储、读模型和未读/已读 owner
- `community-app` 只提供治理规则校验，不再拥有私信读写

优点：

- 与现有前端 `/messages` 实际链路一致
- 与现有 IM 架构方向一致，不推翻已落地的 `seq/read-state/Kafka` 模型
- 删除旧链路后，系统边界清晰，后续需求不会再在两边摇摆

缺点：

- 需要显式删除 legacy 代码，而不是继续“保留着以后再说”

### 3.2 方案 B：`community-app` 对外继续做私信 owner，内部转发到 IM

优点：

- 对 legacy 入口友好

缺点：

- 本质仍是双 owner
- 会新增一层协议翻译和故障面
- 在“无需兼容旧客户端”的前提下没有实际收益

### 3.3 方案 C：把私信再收回 `community-app`

优点：

- 理论上也能实现单 owner

缺点：

- 与当前前端路径和现有 IM 投入方向相反
- 需要废弃或重构 `im-realtime/im-core` 的既有私信模型
- 成本最高、收益最低

结论：采用方案 A。

## 4. 终态架构

### 4.1 单一 owner 规则

终态下，“私信能力”定义为：

- 发起私信
- 实时发送与回执
- 会话列表
- 历史消息
- 未读数与已读水位
- 私信事实数据持久化

以上能力的唯一 owner 为 `community-im`。

### 4.2 各模块职责

#### `im-realtime`

- WebSocket 连接与鉴权
- `sendPrivateText` 协议处理
- 调用 `community-app` 治理校验
- 生产 Kafka command
- 消费 persisted event 并向在线用户推送

#### `im-core`

- 私信事实数据持久化
- 会话读模型
- 历史消息查询
- `lastReadSeq` / 未读状态计算
- 幂等去重（`clientMsgId`）

#### `community-app`

- 提供“是否允许发送私信”的治理规则
- 不再提供任何私信读写 API
- 不再保存任何私信事实数据

### 4.3 数据 owner

终态下：

- 私信唯一事实数据位于 `im-core`
- `community-app.message` 表不再承载私信数据
- 若 `message` 表继续存在，其语义只允许保留给 notice/通知类能力，不能再混入 letters/private-message 逻辑

### 4.4 前端 owner

前端 `/messages` 页面只允许依赖：

- `frontend/src/api/services/imCoreChatService.js`
- `frontend/src/im/imRealtimeClient.js`

不允许再保留：

- `frontend/src/api/services/messageService.js`
- 任何 `/api/messages/**` 调用
- 任何“私信走 HTTP legacy，IM 走 WS/IM”式分支

## 5. 删除范围

### 5.1 必删：legacy 私信对外接口

删除 `community-app` 中以下 legacy 私信能力：

- `MessageController` 中 `/api/messages/**` 的私信路由
- legacy 私信发送、查询、已读相关 use case / query / service
- 任何以 `message` 表为底层的私信读写逻辑

说明：

- 删除是最终目标，不做 deprecated 保留，不做兼容代理
- notice 能力若仍依赖同一模块，应在删除私信后继续拆分

### 5.2 必删：前端 legacy 私信客户端

删除：

- `frontend/src/api/services/messageService.js`
- `frontend/src/api/http.js` 中针对 `/api/messages` 的 `Idempotency-Key` 特判

说明：

- IM 私信不依赖 `Idempotency-Key`
- IM 幂等由 `clientMsgId` 与 `im-core` 去重保证

### 5.3 必清理：命名与模块边界

`community-app` 当前 `message` 域混合了：

- legacy 私信
- notice/通知
- 私信治理规则

删除 legacy 私信后，必须继续做边界清理：

1) notice 相关实现从“message 私信语义”中分离  
2) 私信治理服务从 legacy message 读写语义中分离  
3) 代码结构上不再让开发者误以为 `community-app` 仍然拥有私信读写

推荐方向：

- notice 收敛到 `notice` 语义模块
- 私信治理收敛到 `im.governance` 或等价语义模块

## 6. 迁移路线图

由于不需要兼容、不需要迁历史数据，本次迁移采用“一次性硬切换”的路线。

### 阶段 1：冻结规则与文档先行

目标：

- 在架构和评审规则上先宣布 owner 收敛

动作：

1) 更新设计文档、架构文档，明确 `community-im` 是私信唯一 owner  
2) 在团队约束中声明：任何新私信需求不得落到 `community-app` 的旧 message 私信实现  
3) 将 legacy `/api/messages/**` 视为待删除代码，不再接受任何功能增强

验收：

- 文档中不再把 `community-app` 描述为私信读写 owner

### 阶段 2：删除前端 legacy 入口

目标：

- 从调用侧彻底切断 `/api/messages/**`

动作：

1) 删除 `frontend/src/api/services/messageService.js`  
2) 删除 `frontend/src/api/http.js` 中 `/api/messages` 的幂等特判  
3) 全仓检索并确保无任何前端 import / 调用残留

验收：

- `frontend/src` 中不存在 `/api/messages` 调用
- `/messages` 页面仅依赖 IM HTTP + IM WebSocket

### 阶段 3：删除 backend legacy 私信 API 与实现

目标：

- 从服务端彻底删除旧私信系统

动作：

1) 删除 `/api/messages/**` 路由  
2) 删除 legacy 私信发送、列表、详情、已读 use case / query / service  
3) 删除与 `message` 表私信读写相关的 mapper SQL  
4) 删除对应测试

验收：

- `community-app` 中不存在 `/api/messages/**`
- `community-app` 中不存在基于 `message` 表的私信读写逻辑

### 阶段 4：清理 `community-app` 内部边界

目标：

- 删除“旧 message 域仍拥有私信”的结构误导

动作：

1) 将 notice 相关逻辑从 legacy 私信残骸中拆出  
2) 将私信治理服务移动到独立治理语义模块  
3) 清理 legacy 命名、注释、测试数据和文档

验收：

- `community-app` 中只有 notice 与治理，不再有 private-message read/write owner 代码

### 阶段 5：收尾与防回潮

目标：

- 防止未来重新引入绕行写入口

动作：

1) 检查 `SendPrivateTextCommandV1` producer 是否仍只有 `im-realtime`  
2) 生产环境推荐增加 Kafka ACL，限制 `COMMAND_PRIVATE_TEXT_V1` producer 身份  
3) 将“新增私信写入口必须复用治理校验，并通过 IM 链路进入系统”写入架构约束

验收：

- 代码层没有第二个私信写入口
- 运行期没有绕过治理的非预期 producer

## 7. 风险与处理

### 7.1 风险：notice 与 legacy 私信共用 `message` 域，删除时误伤 notice

处理：

- 删除前先区分“notice 需要保留的最小集合”
- 采用“先切私信调用，再删私信实现，再拆 notice”的顺序

### 7.2 风险：遗漏隐蔽调用方

处理：

- 全仓检索 `/api/messages`
- 全仓检索 `messageService`
- 全仓检索 legacy controller/usecase/service/mapper 的引用

### 7.3 风险：团队习惯导致后续再次在 `community-app` 增私信逻辑

处理：

- 通过代码结构和文档同时约束
- 让 `community-app` 在目录结构上不再具备“私信读写 owner”暗示

## 8. 验收标准

以下条件同时成立，视为本次收敛完成：

1) 前端不存在 legacy `/api/messages/**` 调用  
2) 后端不存在 `/api/messages/**` 路由  
3) `community-app` 中不存在基于 `message` 表的私信读写逻辑  
4) `community-im` 成为唯一私信事实来源  
5) `/messages` 页面只依赖 IM 链路  
6) `community-app` 只保留私信治理规则提供职责  
7) 新增私信需求在代码结构上只能落到 IM

## 9. 本轮实施建议

考虑到当前约束已经非常干净，本轮建议按以下顺序实施：

1) 删除前端 legacy 私信客户端与幂等特判  
2) 删除 backend legacy `/api/messages/**` 及其实现  
3) 清理 `community-app` 中 legacy 私信残留，拆 notice / governance 语义  
4) 补齐文档、测试和架构约束  

本轮不需要：

- 兼容开关
- 双写
- 数据迁移
- 灰度发布策略
- 回滚到旧私信系统的保底路径

在“重新部署、无历史、无兼容诉求”的前提下，继续保留旧链路只会延长双轨状态，而不会带来任何工程收益。
