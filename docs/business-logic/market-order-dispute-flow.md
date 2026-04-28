# 市场订单与争议链路实现说明

本文档说明当前仓库中 `market` 域的实际实现路径，聚焦以下问题：

- 上架、库存、下单分别从哪里进入系统
- 虚拟商品与实物商品的交付差异是什么
- 订单托管、放款、退款如何通过钱包落账
- 取消、争议、卖家处理、管理员裁决如何推进订单状态
- 自动确认是如何触发的

相关文档：

- `docs/business-logic/wallet-ledger-flow.md`

---

## 1. 参与组件

- `MarketController`：市场主入口
- `AdminMarketController`：管理员争议裁决入口
- `MarketApplicationService` / `AdminMarketApplicationService`：HTTP 入口的同域应用服务
- `MarketListingApplicationService`：上架 / 编辑 / 暂停 / 恢复 / 关闭
- `MarketInventoryApplicationService`：预置库存追加 / 作废
- `MarketOrderApplicationService`：下单、虚拟交付、实物发货、确认、取消
- `MarketOrderAutoConfirmApplicationService`：自动确认到期订单
- `MarketDisputeApplicationService`：开争议、卖家接受 / 拒绝、管理员裁决
- `MarketQueryApplicationService`：公开 listing、我的 listing、买单 / 卖单、订单详情
- `MarketWalletActionApplicationService`：写入 `market_wallet_action` 钱包命令
- `MarketWalletActionProcessorApplicationService`：异步调用钱包托管、放款、退款并推进订单 saga
- `MarketWalletActionRecoveryApplicationService`：恢复过期处理租约、对账未完成的钱包命令
- `WalletMarketActionApi`：market 跨域进入 wallet 的同步 action contract，由 wallet adapter 委托 `WalletMarketApplicationService` 落账
- XXL Job：
  - `MarketOrderAutoConfirmHandler`
  - `MarketWalletActionProcessorHandler`
  - `MarketWalletActionRecoveryHandler`

---

## 2. 对外接口

主要接口在：

- listing：
  - `GET /api/market/listings`
  - `GET /api/market/listings/{listingId}`
  - `GET /api/market/my-listings`
  - `POST /api/market/listings`
  - `PUT /api/market/listings/{listingId}`
  - `POST /api/market/listings/{listingId}/pause`
  - `POST /api/market/listings/{listingId}/resume`
  - `POST /api/market/listings/{listingId}/close`
- inventory：
  - `GET /api/market/listings/{listingId}/inventory`
  - `POST /api/market/listings/{listingId}/inventory`
  - `POST /api/market/inventory/{inventoryUnitId}/invalidate`
- order：
  - `POST /api/market/orders`
  - `GET /api/market/orders/buying`
  - `GET /api/market/orders/selling`
  - `GET /api/market/orders/{orderId}`
  - `POST /api/market/orders/{orderId}/deliver`
  - `POST /api/market/orders/{orderId}/ship`
  - `POST /api/market/orders/{orderId}/confirm`
  - `POST /api/market/orders/{orderId}/cancel`
- dispute：
  - `POST /api/market/orders/{orderId}/disputes`
  - `POST /api/market/disputes/{disputeId}/seller-accept`
  - `POST /api/market/disputes/{disputeId}/seller-reject`
  - `GET /api/admin/market/disputes`
  - `POST /api/admin/market/disputes/{disputeId}/resolve-refund`
  - `POST /api/admin/market/disputes/{disputeId}/resolve-release`

---

## 3. listing 与 inventory

### 3.1 listing 状态

当前 listing 状态包括：

- `ACTIVE`
- `PAUSED`
- `SOLD_OUT`
- `CLOSED`

`MarketListingApplicationService` 负责这些状态切换。

### 3.2 虚拟与实物的创建差异

#### 虚拟商品

需要区分：

- `deliveryMode`
  - `PRELOADED`
  - `MANUAL`
- `stockMode`
  - `FINITE`
  - `UNLIMITED`

`PRELOADED` 还有一个额外要求：

- 必须同时提交库存 payload
- `stockTotal` 必须等于 payload 数量

#### 实物商品

实物商品不使用虚拟商品的 `deliveryMode` / `stockMode` / 预置库存批次。

### 3.3 预置库存

`MarketInventoryApplicationService.appendInventory(...)` 只服务于：

- `goodsType=VIRTUAL`
- `deliveryMode=PRELOADED`

库存单元状态包括：

- `AVAILABLE`
- `INVALID`
- 订单处理中还会进入 `RESERVED`
- 交付后会变成 `DELIVERED`

---

## 4. 下单与托管

### 4.1 下单主链路

`POST /api/market/orders` -> `MarketApplicationService.createOrder(...)` -> `MarketOrderApplicationService.createOrder(...)`

关键步骤：

1. controller 读取 `Idempotency-Key`；旧 body `requestId` 仅在 header 缺失时 fallback，header/body 不一致返回 `400`
2. `IdempotencyGuard` 按 `buyerUserId + operation + key + requestHash` 包裹下单，同 key 不同 payload 返回 replay conflict
3. 按 `(buyer_user_id, request_id)` 查重，已有订单且语义一致直接返回
4. 锁定 listing，要求必须 `ACTIVE`
5. 校验：
   - 买家不能买自己的 listing
   - 数量必须落在 `min/max purchase quantity`
   - 有限库存必须足够
6. 如果是预置虚拟商品：
   - 预留足够数量的 `AVAILABLE` inventory unit，状态变为 `RESERVED`
7. 生成 `orderId`，计算 `totalAmount`
8. 写入 `market_order(status=ESCROW_PENDING, request_id=<effective idempotency key>)`
9. 对有限库存执行扣减
10. 写入 `market_wallet_action(action_type=ESCROW, status=PENDING, request_id=market-order:<orderId>:escrow)`
11. 后台 processor 调 `WalletMarketActionApi.escrowOrder(...)`
12. 钱包成功后由 `MarketOrderSagaApplicationService` 推进到 `ESCROWED`
13. 如果是预置虚拟商品：
    - escrow 成功后把预留库存交付给买家
    - 订单进入 `DELIVERED`
    - 同时写入 `autoConfirmAt = now + 24h`

关键点：

- 市场订单不直接改余额
- 对外幂等 key 与钱包账本 request id 解耦；钱包侧使用 `market-order:<orderId>:<action>` 这类服务端派生 id
- 资金动作不再在 `MarketOrderApplicationService` / `MarketDisputeApplicationService` 的同步事务内直接落钱包账本。
- 市场先写 `market_wallet_action`，订单进入 `ESCROW_PENDING`、`RELEASE_PENDING`、`REFUND_PENDING`
  或争议 pending 状态；后台 processor 调钱包并回写最终状态。

### 4.2 订单主要状态

从当前代码看，订单的核心状态包括：

- `ESCROWED`
- `ESCROW_PENDING`
- `ESCROW_CANCEL_PENDING`
- `ESCROW_FAILED`
- `DELIVERED`
- `SHIPPED`
- `RELEASE_PENDING`
- `COMPLETED`
- `DISPUTED`
- `REFUND_PENDING`
- `DISPUTE_REFUND_PENDING`
- `DISPUTE_RELEASE_PENDING`
- `REFUNDED`
- `CANCELLED`

下单后不会长期停留在 `CREATED`，而是先进入 `ESCROW_PENDING`。钱包托管成功后才进入 `ESCROWED`。

---

## 5. 交付、发货、确认、取消

### 5.1 虚拟商品手动交付

`POST /api/market/orders/{orderId}/deliver` -> `deliverVirtualOrder(...)`

约束：

- 只能卖家操作
- 只能虚拟商品
- 只能 `ESCROWED`
- 只能 `MANUAL` delivery

落地动作：

- 写一条 `market_delivery`
- 订单改成 `DELIVERED`
- `autoConfirmAt = now + 24h`

### 5.2 实物发货

`POST /api/market/orders/{orderId}/ship` -> `shipPhysicalOrder(...)`

约束：

- 只能卖家操作
- 只能实物商品
- 只能 `ESCROWED`

落地动作：

- 写一条 `market_shipment`
- 订单改成 `SHIPPED`
- `autoConfirmAt = now + 7d`

### 5.3 买家确认

`POST /api/market/orders/{orderId}/confirm` -> `confirmOrder(...)`

允许状态：

- `DELIVERED`
- `SHIPPED`

资金动作：

- 订单先改成 `RELEASE_PENDING`
- 写入 `market_wallet_action(action_type=RELEASE)`
- 后台 processor 放款，资金从 `ORDER_ESCROW -> USER_WALLET:seller`

然后：

- 放款成功后订单改成 `COMPLETED`

### 5.4 买家取消

`POST /api/market/orders/{orderId}/cancel` -> `cancelOrder(...)`

允许状态：

- `ESCROWED`
- `ESCROW_PENDING`

资金动作：

- `ESCROWED` 订单先改成 `REFUND_PENDING`
- 写入 `market_wallet_action(action_type=REFUND)`
- 后台 processor 退款

然后：

- 退款成功后有限库存回补
- 预置虚拟库存释放回 `AVAILABLE`
- 订单改成 `CANCELLED`

如果买家在 `ESCROW_PENDING` 时取消，订单会进入 `ESCROW_CANCEL_PENDING` 或直接 `CANCELLED`。后台 processor 会避免再执行无意义托管；如果托管已经成功，则补发退款命令。

---

## 6. 争议链路

### 6.1 买家发起争议

`POST /api/market/orders/{orderId}/disputes` -> `openDispute(...)`

约束：

- 只能买家本人
- 订单必须是 `DELIVERED` 或 `SHIPPED`
- 一个订单只允许一个 active dispute

落地动作：

- 插入 `market_dispute(status=OPEN)`
- 订单改成 `DISPUTED`

### 6.2 卖家接受退款

`sellerAcceptRefund(...)`

资金动作：

- dispute -> `SELLER_ACCEPTED`
- `resolutionType=REFUND`
- 订单 -> `DISPUTE_REFUND_PENDING`
- 写入 `market_wallet_action(action_type=REFUND)`

然后：

- 退款成功后订单 -> `REFUNDED`

### 6.3 卖家拒绝退款

`sellerRejectRefund(...)`

落地动作：

- dispute -> `SELLER_REJECTED`

订单仍保持 `DISPUTED`，等待管理员介入。

### 6.4 管理员裁决

管理员有两种裁决动作：

- `adminResolveRefund(...)`
- `adminResolveRelease(...)`

分别对应：

- 退款给买家
- 放款给卖家

管理员裁决先把订单推进到 `DISPUTE_REFUND_PENDING` 或 `DISPUTE_RELEASE_PENDING` 并写入对应 `market_wallet_action`。

两者都会把 dispute 改成：

- `ADMIN_RESOLVED`

并写入：

- `resolvedBy`
- `resolvedAt`

---

## 7. 自动确认

自动确认对外 action contract 由 `MarketOrderAutoConfirmActionApiAdapter` 实现：

- `MarketOrderAutoConfirmActionApi.autoConfirmDueOrders()`

当前逻辑：

1. 查所有到期订单
2. 只处理：
   - 当前仍是 `DELIVERED` 或 `SHIPPED`
   - `autoConfirmAt <= now`
3. 复用 `confirmOrder(...)`
4. `completedCount` 表示成功排队 release 命令的数量；最终 `COMPLETED` 由钱包 action processor 回写

调度入口是：

- `backend/community-app/src/main/java/com/nowcoder/community/infra/job/handlers/MarketOrderAutoConfirmHandler.java`

也就是说，自动确认不是单独一套放款逻辑，而是复用人工确认路径，并由后台钱包命令完成最终放款。

---

## 8. 查询侧

`MarketQueryApplicationService` 负责：

- 公共 listing 列表
- listing 详情
- 我的上架
- 我的买单 / 卖单
- 订单详情

订单详情的一个关键点是：

- 虚拟商品会优先读 `market_delivery`
- 如果没有手动交付记录，再从预置库存单元里拼 delivery content

这保证了预置交付和手动交付能通过同一个详情接口读出来。

---

## 9. 失败语义

常见失败点：

- listing 不存在或不在 `ACTIVE`
- 买家购买自己的商品
- 数量超限
- 有限库存不足
- 预置库存不足
- 地址不存在或不属于买家
- 订单状态与当前动作不匹配
- dispute 已存在或不可裁决

这些都属于主链路内的强约束，会直接终止交易状态推进。

---

## 10. 一句话总结

当前市场实现的核心思路是：

- listing / inventory / order / dispute 各自维护自己的业务状态
- 钱相关动作先落 `market_wallet_action`，再由后台 processor 委托钱包域做托管、放款、退款
- 预置虚拟商品在托管成功后自动交付，手动虚拟与实物则要等卖家动作
- 争议只在已交付 / 已发货后打开，最终以退款或放款收敛
