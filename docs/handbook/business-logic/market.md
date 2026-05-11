# Market 市场业务逻辑

市场域提供社区内交易能力：商品发布、库存、地址、订单、交付、发货、确认、取消、纠纷和资金动作 saga。market owns 交易业务事实；wallet owns 资金事实。

## Owner / SSOT

- market owns listing、inventory unit、address、order、delivery、shipment、dispute、market wallet action。
- wallet owns 钱包账户、余额和总账交易。
- user owns 买家/卖家账号事实。

市场写路径的关键原则：HTTP 成功不一定表示资金已经落账。订单可能进入 `ESCROW_PENDING`、`RELEASE_PENDING`、`REFUND_PENDING` 等 pending 状态，随后由后台 processor 调 wallet 完成。

## 入口

用户接口：

- `GET /api/market/listings`
- `GET /api/market/listings/{listingId}`
- `GET /api/market/my-listings`
- `POST /api/market/listings`
- `PUT /api/market/listings/{listingId}`
- `POST /api/market/listings/{listingId}/pause`
- `POST /api/market/listings/{listingId}/resume`
- `POST /api/market/listings/{listingId}/close`
- `GET /api/market/listings/{listingId}/inventory`
- `POST /api/market/listings/{listingId}/inventory`
- `POST /api/market/inventory/{inventoryUnitId}/invalidate`
- `GET /api/market/addresses`
- `POST /api/market/addresses`
- `PUT /api/market/addresses/{addressId}`
- `DELETE /api/market/addresses/{addressId}`
- `POST /api/market/orders`
- `GET /api/market/orders/buying`
- `GET /api/market/orders/selling`
- `GET /api/market/orders/{orderId}`
- `POST /api/market/orders/{orderId}/deliver`
- `POST /api/market/orders/{orderId}/ship`
- `POST /api/market/orders/{orderId}/confirm`
- `POST /api/market/orders/{orderId}/cancel`
- `POST /api/market/orders/{orderId}/disputes`
- `POST /api/market/disputes/{disputeId}/seller-accept`
- `POST /api/market/disputes/{disputeId}/seller-reject`

管理接口：

- `GET /api/admin/market/disputes`
- `POST /api/admin/market/disputes/{disputeId}/resolve-refund`
- `POST /api/admin/market/disputes/{disputeId}/resolve-release`

后台任务：

- `MarketWalletActionProcessorHandler`
- `MarketWalletActionRecoveryHandler`
- `MarketOrderAutoConfirmHandler`

## 数据流

市场域的数据流的核心是“订单状态”和“资金 saga”分离：

1. 下单：`MarketOrderApplicationService` 先校验 listing、库存、地址和总额，再创建订单并把状态置为 `ESCROW_PENDING`。如果是有限库存或预加载虚拟库存，库存会先被锁定或扣减。
2. 托管：市场本地只写 `market_wallet_action(ESCROW, PENDING)` 作为 durable command，不直接碰 wallet 账本。`request_id` 由订单和动作派生，保证重复 enqueue 语义一致。
3. processor：`MarketWalletActionProcessorHandler` 轮询 due action，claim 后在 market 事务外调用 `WalletMarketActionApi`。wallet 成功后返回 `wallet_txn_id`，market 再把订单推进到 `ESCROWED`、`RELEASE_PENDING`、`REFUND_PENDING` 或完成态。
4. 确认 / 取消 / 争议：卖家交付、买家确认、买家取消和管理员裁决都只是改变 market 侧状态并追加 release/refund action；真正放款或退款仍由 wallet owner 完成。
5. 恢复：`MarketWalletActionRecoveryHandler` 负责恢复过期 lease、补齐漏写 command、把已有 `wallet_txn_id` 重新应用到 saga 状态，避免钱包和订单长期分叉。

## 商品和库存

`MarketListingApplicationService`：

- 创建商品。
- 更新商品标题、描述、价格、购买限制等。
- 暂停商品。
- 恢复商品。
- 关闭商品。

创建商品的基础规则：

- sellerUserId 必须存在。
- 标题必须非空。
- 单价必须为正。
- 初始库存必须符合 stock mode 和 goods type。
- 最小购买数量不能大于最大购买数量。

库存：

- 实物商品天然有限库存。
- 虚拟商品可使用无限库存或预加载库存。
- 预加载库存只允许 `goodsType=VIRTUAL` 且 `deliveryMode=PRELOADED`。
- 每条预加载库存是一个 `MarketInventoryUnit`，包含 payloadType 和 payload。
- 追加库存会增加 listing `stock_total` 和 `stock_available`。
- 如果 listing 因售罄处于 `SOLD_OUT`，追加可用库存后可恢复 `ACTIVE`。
- 失效库存只允许卖家操作，且只能失效 `AVAILABLE` unit。

## 地址

`MarketAddressApplicationService`：

- 创建地址。
- 查询当前用户地址列表。
- 更新地址。
- 删除地址。

规则：

- 所有地址操作都要求 userId 非空。
- 创建和更新要求 receiverName、receiverPhone、province、city、district、detailAddress 非空。
- 所有文本字段写入前 trim。
- postalCode 可空；非空时 trim。
- 地址状态写入为 `ACTIVE`。
- 创建地址使用 UUIDv7 addressId。
- 创建或更新时如果 `defaultAddress=true`，会先清除该用户已有默认地址，再保存当前地址为默认。
- 查询只返回 repository 中该用户地址列表。
- 更新和删除前都必须通过 `requireOwnedAddress(...)` 校验地址存在、状态 active 且属于当前用户。
- 地址不存在或不是 active 返回 NOT_FOUND；地址不属于当前用户返回参数错误。
- 删除是 soft delete，不物理删除历史地址行。

地址是买家下实物订单时的输入。订单创建时保存地址快照，后续用户修改地址不会影响已创建订单。

## 查询

`MarketQueryApplicationService`：

- 公开 listing 列表。
- listing 详情。
- 卖家自己的 listing。
- 买家订单列表。
- 卖家订单列表。
- 订单详情。

订单详情访问规则：

- 只有订单买家或卖家可查看。
- 虚拟商品订单详情可返回 delivery content。
- 物理商品订单详情不返回虚拟交付内容。
- 预加载虚拟商品优先从已交付库存读取 payload；手动交付则读取 delivery 记录。

## 下单

`MarketOrderApplicationService.createOrder(...)`：

1. controller 只读取 `Idempotency-Key` 作为 HTTP 幂等键。
2. body `requestId` 按未知字段返回参数错误。
3. application 按 buyer + requestId 查询已有订单；存在则校验 replay 是否一致。
4. 锁定 listing。
5. 校验 listing active。
6. 校验买家不能是卖家。
7. 校验购买数量、库存和订单总额上限。
8. 预加载虚拟商品锁定指定数量的 available inventory units。
9. 实物商品校验 active 地址并保存地址快照。
10. 创建订单，初始状态 `ESCROW_PENDING`。
11. 有限库存扣减 listing 可用库存；扣到 0 时 listing 变 `SOLD_OUT`。
12. 预加载库存绑定到订单。
13. 写 `market_wallet_action` 的 ESCROW durable command。
14. 返回重新加载后的订单结果。

下单成功表示订单已被接受并进入资金托管处理中，不表示 wallet 已经完成扣款。

## 交付、发货、确认和取消

虚拟手动交付：

1. 卖家操作。
2. 订单必须属于该卖家。
3. goodsType 必须是 `VIRTUAL`。
4. 状态必须是 `ESCROWED`。
5. deliveryMode 必须是 `MANUAL`。
6. 写 `MarketDelivery`。
7. 标记订单 `DELIVERED`，并设置自动确认时间。

实物发货：

1. 卖家操作。
2. goodsType 必须是 `PHYSICAL`。
3. 状态必须是 `ESCROWED`。
4. carrierName 和 trackingNo 必须非空。
5. 写 shipment。
6. 标记订单 `SHIPPED`，并设置自动确认时间。

买家确认：

1. 买家操作。
2. 状态必须是 `DELIVERED` 或 `SHIPPED`。
3. 将订单标记为 `RELEASE_PENDING`。
4. 写 RELEASE wallet action。

买家取消：

- 如果订单 `ESCROW_PENDING`，进入 `ESCROW_CANCEL_PENDING`；若 escrow command 尚未处理，可取消为 no-op 并恢复库存。
- 如果订单 `ESCROWED`，进入 `REFUND_PENDING` 并写 REFUND wallet action。
- 其他状态拒绝取消。

## 纠纷

买家发起纠纷：

1. 只有买家可发起。
2. 订单必须是 `DELIVERED` 或 `SHIPPED`。
3. 同订单不能已有 active dispute。
4. 写 dispute，状态 `OPEN`。
5. 订单标记 `DISPUTED`。

卖家处理：

- `sellerAcceptRefund(...)`：卖家接受退款，dispute 变 `SELLER_ACCEPTED`，订单进入 dispute refund pending，写 DISPUTE_REFUND wallet action。
- `sellerRejectRefund(...)`：卖家拒绝退款，dispute 变 `SELLER_REJECTED`，等待管理员裁决。

管理员裁决：

- `adminResolveRefund(...)`：裁退款，写 DISPUTE_REFUND wallet action。
- `adminResolveRelease(...)`：裁放款，写 DISPUTE_RELEASE wallet action。

active dispute 包括 `OPEN` 和 `SELLER_REJECTED`。

## Market wallet action saga

`market_wallet_action` 是 market 到 wallet 的 durable business command，不是普通事件投影。

创建 command：

- ESCROW：下单后写入。
- RELEASE：买家确认或自动确认后写入。
- REFUND：买家取消或争议裁退款后写入。
- DISPUTE_RELEASE：管理员裁放款后写入。

requestId 规则由 `MarketWalletActionDomainService` 生成，形如 `market-order:<orderId>:<action>`。重复 enqueue 必须语义一致。

processor：

1. `MarketWalletActionProcessorHandler` 触发 `processDue(limit)`。
2. application claim due action，设置 `PROCESSING` 和 lease。
3. 在 market 事务外调用 `WalletMarketActionApi`。
4. wallet 成功返回 walletTxnId。
5. action 标记成功，并由 `MarketOrderSagaApplicationService` 条件推进订单/争议状态。
6. 可恢复失败进入 retry/backoff。
7. 终态失败保留失败状态供 recovery 或人工排查。

saga 状态推进：

- escrow 成功：订单进入 `ESCROWED`；预加载虚拟商品可自动 delivery。
- escrow 被取消且尚未落账：订单 no-op 完成并恢复库存。
- escrow 已落账但订单进入取消路径：补写 refund command。
- release 成功：订单完成。
- refund 成功：订单退款完成。

## 自动确认

`MarketOrderAutoConfirmApplicationService.autoConfirmDueOrders()`：

1. 查找达到自动确认时间的订单。
2. 每个订单由 `MarketOrderAutoConfirmSingleOrderApplicationService.confirmOneDueOrder(...)` 单独锁定。
3. 状态仍符合条件时，标记 `RELEASE_PENDING`。
4. 写 release wallet action。
5. 批任务重跑应幂等。

自动确认不直接调 wallet。

## 恢复任务

`MarketWalletActionRecoveryApplicationService`：

- `recoverExpiredProcessing(...)`：恢复 lease 过期的 PROCESSING action。
- `reconcileOnce(limit)`：补齐缺失 command、复用已有 walletTxnId 继续推进 saga、修复可自动恢复的 pending。

恢复任务用于处理 processor 调 wallet 成功但 market 状态推进失败、processor 崩溃、lease 卡住等情况。

## 关键状态

常见订单状态：

- `ESCROW_PENDING`
- `ESCROW_CANCEL_PENDING`
- `ESCROWED`
- `DELIVERED`
- `SHIPPED`
- `RELEASE_PENDING`
- `REFUND_PENDING`
- `DISPUTED`
- `DISPUTE_REFUND_PENDING`
- `DISPUTE_RELEASE_PENDING`
- 完成/退款/失败类终态

常见 wallet action 状态：

- `PENDING`
- `PROCESSING`
- `RETRYING`
- `SUCCEEDED`
- `FAILED`
- `CANCELLED` / no-op 类状态

## 关键代码

- `market.controller.MarketController`
- `market.controller.AdminMarketController`
- `market.application.MarketApplicationService`
- `market.application.MarketQueryApplicationService`
- `market.application.MarketListingApplicationService`
- `market.application.MarketInventoryApplicationService`
- `market.application.MarketAddressApplicationService`
- `market.application.MarketOrderApplicationService`
- `market.application.MarketDisputeApplicationService`
- `market.application.MarketWalletActionApplicationService`
- `market.application.MarketWalletActionProcessorApplicationService`
- `market.application.MarketWalletActionRecoveryApplicationService`
- `market.application.MarketOrderSagaApplicationService`
- `market.application.MarketOrderAutoConfirmApplicationService`
- `market.domain.service.*`
- `market.infrastructure.job.*`
- `wallet.api.action.WalletMarketActionApi`
