# 前端业务状态与 API 编排

本页覆盖收窄后的前端核心逻辑：业务状态投影、API 编排、路由到页面能力映射。纯展示组件、样式、布局壳、图标、文案 helper，以及没有业务语义的格式化工具不纳入本页。

前端不是业务事实 owner。这里的状态都是后端 owner fact 的客户端投影，用于页面组装、交互约束、错误恢复和一致性提示；最终事实仍以后端 auth、content、social、market、wallet、drive、notice、search、IM 等域为准。

## API 编排

这些 service 是当前前端主要 API 编排入口：

| 文件 | 当前行为 |
| --- | --- |
| `authService.js` | 登录、当前用户、refresh、logout、注册、注册验证码重发 / 验证、captcha、找回 / 重置密码；所有响应经 `unwrapResultBody` 取 `data` / `traceId`，`refresh({ silent: true })` 跳过全局错误提示。 |
| `userService.js` | 用户主页、最近帖子 / 评论、批量用户摘要；`getUserProfile` 按 userId 做 cache 和 inflight 合并，并补齐用户等级展示字段。 |
| `socialService.js` | 点赞、关注、取关、关注状态、粉丝 / 关注列表、批量点赞数 / 状态；本地 cache 只降低重复请求，不是最终社交事实。 |
| `postService.js` | 帖子列表 / 创建 / 详情 / 编辑 / 删除、评论 / 回复列表和写入、置顶 / 加精 / 删除治理动作；创建和编辑会规范化 block 与 opaque id。 |
| `postMediaService.js` | 帖子媒体上传会话创建、文件上传执行、`mediaKind` 推断；上传结果以会话返回的 `assetId` / `uploadId` 为关联点。 |
| `driveService.js` | 网盘空间、目录、回收站、文件夹、搜索、上传会话、重命名 / 移动 / 删除 / 恢复 / 彻删、下载链接、分享创建 / 撤销、公有分享校验和下载。 |
| `marketService.js` | 商品列表 / 详情 / 发布、我的出售、库存、买卖订单、交付 / 发货 / 确认 / 取消 / 申诉、管理员争议裁定、收货地址。 |
| `walletService.js` | 钱包概览、流水、充值、提现、转账、管理员冻结和交易回滚。 |
| `imCoreChatService.js` | IM core 会话列表、按 `afterSeq` 拉取会话消息、标记已读；使用 `imCoreHttp` 而不是主应用 `http`。 |

以下文件当前只是薄 endpoint mapping、参数整理或响应拆包，保持 `IndexOnly`：`adminUserService.js`、`moderationService.js`、`noticeService.js`、`reportService.js`、`blockService.js`、`taxonomyService.js`、`subscriptionService.js`、`bookmarkService.js`、`searchService.js`、`analyticsService.js`。

## 业务状态

| 文件 | 当前状态语义 |
| --- | --- |
| `registerFlowState.js` | 保存待验证注册上下文：`registrationToken`、`userId`、`emailCodeIssued`、`maskedEmail`、`debugEmailCode`。只有 `step === 'verify'` 会写入 `localStorage` 的 `community.register.pending`；回到表单态会清理。恢复时 JSON 解析失败会删除本地状态。错误码 `10002`、`10013`、`10014`、`11001` 会返回 `resetFlow: true`，要求页面重置注册上下文。 |
| `conversationDetailState.js` | 生成 canonical conversation id：两个 userId 按 Java UUID signed bits 排序后拼接。消息映射要求合法 `messageId`、`fromUserId`、`toUserId`、正数 `seq` 和 `createdAtEpochMs`。消息合并优先用 `seq` 去重，缺少 `seq` 时用 message id；排序按 `seq`、时间、id。`findLatestConversationSeq` 提供 catch-up cursor。 |
| `marketState.js` | 将 listings、orders、disputes、addresses、inventory 投影为页面可读状态：商品类型、履约、托管、库存、订单下一步、资金状态、争议状态、地址行和订单 lifecycle steps。它只解释当前返回数据，不推进市场 / 钱包事实。 |
| `driveState.js` | 规范化 quota、面包屑、entry 能力和分享表单。entry capability 由 `status` / `type` 派生：ACTIVE 文件可下载，ACTIVE 条目可分享 / 重命名 / 移动 / 移入回收站，TRASHED 条目可恢复 / 彻删。分享表单要求提取码非空且过期时间晚于当前时间。 |
| `walletState.js` | 将 summary 投影为余额和钱包状态提示，将 txns 投影为流水 feed。金额正负决定转账进出标签，状态缺省为 `SUCCEEDED`，余额展示下限为 0。 |
| `postsViewState.js` | 负责发帖标签规则和列表 hydration id 收集。标签去掉前导 `#`，空白转 `-`，最多 5 个，单个最长 20，只允许中英文、数字、`_`、`-`，重复标签按大小写不敏感忽略。hydration 最多收集 200 个 userId 和 200 个 postId。 |
| `postDetailState.js` | 收集评论 / 回复 hydration id，回复会额外收集 `targetId`。评论 hydration 组装 user、like count、liked 和回复 UI 状态；回复 hydration 组装 user、targetUser、like count、liked。引用内容会压缩空白并生成最多 6 行 quote markdown。 |

## 路由到页面能力

`frontend/src/router/index.js` 是 route-to-page 映射，`frontend/src/router/navigation.js` 是导航和 workspace SSOT。`meta.requiresAuth` 控制登录门槛，`meta.roles` 控制管理入口，shell 搜索当前只在 `posts`、`search`、`market` 生效。

| 能力区域 | 当前路由 / 页面 |
| --- | --- |
| 认证 | `/auth/login`、`/auth/register`、`/auth/password/reset` 对应登录、注册、找回密码页面。 |
| 社区内容 | `/posts`、`/posts/:postId`、`/search`、`/users/:userId`、`/users/:userId/followees`、`/users/:userId/followers`、`/bookmarks` 映射帖子流、详情、搜索、成员主页、关系和收藏能力。 |
| 交易与资产 | `/market`、`/market/listings/:listingId`、`/market/publish`、`/market/my-listings`、`/market/my-listings/:listingId/inventory`、`/market/orders/buying`、`/market/orders/selling`、`/market/orders/:orderId`、`/market/addresses`、`/wallet` 映射市场、订单、库存、地址和钱包能力。 |
| 文件 | `/drive` 需要登录，映射私有网盘；`/drive/s/:shareToken` 是公有分享访问入口。 |
| 收件箱 | `/messages`、`/messages/:conversationId`、`/notices`、`/notices/:topic` 映射 IM 会话和通知主题。 |
| 运营 | `/moderation`、`/analytics` 需要 `ROLE_ADMIN` 或 `ROLE_MODERATOR`；`/admin/users`、`/admin/wallet`、`/admin/market/disputes` 需要 `ROLE_ADMIN`。 |

## 失败与一致性

API success 只表示对应 HTTP/API 调用按当前后端语义完成或被接受，不表示所有异步投影已经追平。发帖后 search 结果可能滞后；互动、关注、治理等 notice 可能稍后出现；IM 写入被接受后，列表、历史、未读和已读状态仍要以 core projection / history catch-up 为准。

前端 cache 和 hydration 是性能与页面装配手段。`userService.js`、`socialService.js` 的本地 cache 不承担跨页面强一致；页面需要强制刷新时应使用已有 `force` 或重新拉取 owner API。market / wallet 页面看到 `*_PENDING`、`ESCROWED`、`HELD` 等状态时，只能展示处理进度，不能推断资金动作已经最终完成。
