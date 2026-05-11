# 核心业务 FAQ

## 为什么认证和用户要分成 auth / user？

auth 负责“如何进入系统”：注册流程、验证码、登录风控、JWT 签发、refresh token 旋转。user 负责“用户事实是什么”：账号、密码 hash、邮箱、角色、处罚状态、refresh session 存储事实。这样注册、登录和密码重置可以通过 user owner API 改用户事实，而不会让 auth 直接写 user 表。

## 为什么发帖成功后，搜索或通知可能还没出现？

发帖成功代表 content 主事实已经提交。搜索索引通过 outbox worker 最终追平，通知通过 after-commit best-effort 投影生成。HTTP 成功不表示这些下游读模型已经完成。

## 为什么 search 不能当成帖子事实来源？

Elasticsearch 只服务搜索查询。帖子标题、正文、标签、媒体、删除和治理状态的主事实在 content。搜索缺失或过期时，应排查 outbox、projection 或 reindex，而不是把 ES 当成可写事实。

## 为什么 notice 不撤销部分通知？

notice 是由上游事件生成的读模型。当前点赞取消或取消关注不一定撤销已经生成的通知，具体语义以 notice 投影规则为准。是否撤销通知应该作为 notice 业务规则显式设计，不能由上游直接改 notice 表。

## 为什么点赞要回源 content？

点赞事件需要知道目标用户、帖子归属和实体状态，这些事实属于 content。social 只拥有点赞关系，不能信任客户端传入的 `entityUserId` 或 `postId`。

## 为什么拉黑会影响 IM，但拉黑事实不在 IM？

拉黑关系属于 social。IM realtime 只维护本地 policy projection，用于发送前快速判定。projection 由 social/user 的 snapshot 和增量事件驱动，不能当成拉黑 SSOT。

## 为什么 growth 不直接改用户积分？

growth 只决定任务进度、是否达成和是否触发奖励。真正的余额和入账事实属于 wallet，由 wallet 的账本规则和 requestId 去重保障一致性。

## 为什么 wallet 用复式账本？

钱包要表达资金从一个账户流向另一个账户。复式账本让每笔交易都有 debit / credit，对账和回滚语义更清晰，也能让 market、growth、admin adjustment 使用同一个资金事实入口。

## 为什么 market 订单和 wallet 资金动作分开？

market 拥有商品、库存、地址快照、订单和纠纷；wallet 拥有账户余额和账本。下单、确认收货、退款等动作会写 market 状态，并通过资金 action / saga 进入 wallet。订单成功和资金完成不是同一个成功条件。

## 为什么 drive 和 OSS 也要分开？

drive 是用户网盘业务：目录树、配额、回收站、分享链接、提取码。OSS 是文件对象技术事实：对象、版本、alias、签名 URL、引用和 blob 生命周期。业务授权先由 drive 判断，再由 OSS 发放具体访问能力。

## 为什么 IM 不直接放在 community-app？

IM 有独立的连接态、实时推送、Kafka command、消息持久化、未读和成员状态。它拆成 gateway、realtime、core 后，主站只提供用户 policy 和拉黑关系，消息权威事实由 `im-core` 负责。

## 为什么 controller 不能直接调 foreign API 或 repository？

controller 是 inbound adapter，只做 HTTP binding、身份提取、校验入口和 DTO 转换。跨域协作、事务、幂等、domain 调用和 foreign API 调用都必须进入同领域 ApplicationService 后处理。这样才能保持 DDD 边界和 ArchUnit 守卫一致。

## 新增业务文档应该写在哪里？

项目相关文档写在 `docs/handbook` 下。核心业务说明写在 `docs/handbook/business-logic` 下；如果是一个完整用户动作，优先放到 `docs/handbook/business-logic/workflows`；如果是单个 owner domain 的详细行为，放到对应领域文档。
