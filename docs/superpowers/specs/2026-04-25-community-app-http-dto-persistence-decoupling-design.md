# Community App HTTP DTO 与持久化实体解耦设计稿

**Date:** 2026-04-25
**Status:** Approved for planning
**Owner:** Codex

---

## 1. Goal

彻底解决 `backend/community-app/` 内 HTTP DTO 直接依赖持久化实体的问题，确保 API 传输模型、应用层输出模型、表模型各自独立演进。

本设计要解决的根问题不是某几个 `from(Entity)` 方法的位置，而是当前依赖方向错误：

- `dto` 包直接 import `entity`，例如 `MarketOrderResponse.from(MarketOrder)`、`CreateRechargeResponse.from(RechargeOrder)`
- service 直接返回 HTTP response DTO，导致业务服务被 HTTP 传输模型绑定
- controller 基本透传 service 返回值，HTTP 边界没有承担最后一跳的 DTO 适配
- 表字段命名、字段拆分、字段类型变化容易外溢到 API 层和 controller-facing service 合同

终态要求：

- HTTP DTO 不依赖 `entity`、`mapper`、`dao`
- controller-facing `ApplicationService` 不返回 HTTP response DTO
- 持久化实体只在 owner 域内部的 service/mapper/repository 层流转
- HTTP response DTO 只从应用层 result/view model 构造
- 通过 ArchUnit 规则防止新 DTO 重新依赖实体

---

## 2. Scope And Non-Goals

### 2.1 In Scope

- `backend/community-app/src/main/java/com/nowcoder/community/*/dto`
- `market`、`wallet`、`content` 中已发现的 DTO -> entity 依赖
- controller、`ApplicationService`、domain service 的返回模型边界
- MyBatis mapper 返回 entity 与应用层 read model 的边界
- 架构测试与迁移基线策略

### 2.2 Out Of Scope

- 数据库 schema 重命名、拆表、合表
- 对外 HTTP JSON 字段的主动破坏性改名
- Maven module 拆分
- 把 `community-app` 改造成重型 CQRS / CommandBus 架构
- 删除已有 owner-domain `api.query` / `api.action` / `api.model` 跨域协作面

### 2.3 Compatibility Rule

第一阶段默认保持现有 HTTP JSON 结构不变。

如果需要把 `unitPriceSnapshot`、`deliveryModeSnapshot` 这类字段改成更 API-friendly 的名称，应作为单独 API 兼容性改造处理，而不是混在本次解耦里。当前字段名可以暂时保留在 DTO 中，但它们必须来自应用层 result/view model，而不能来自表实体。

---

## 3. Current Problem

### 3.1 DTO 直接依赖实体

当前至少存在以下同类依赖：

- `market/dto/MarketOrderResponse.java` -> `market/entity/MarketOrder`
- `market/dto/MarketOrderDetailResponse.java` -> `market/entity/MarketOrder`, `market/entity/MarketShipment`
- `market/dto/MarketListingResponse.java` -> `market/entity/MarketListing`
- `market/dto/MarketListingDetailResponse.java` -> `market/entity/MarketListing`
- `market/dto/MarketAddressResponse.java` -> `market/entity/MarketAddress`
- `market/dto/MarketInventoryUnitResponse.java` -> `market/entity/MarketInventoryUnit`
- `market/dto/MarketDisputeResponse.java` -> `market/entity/MarketDispute`
- `wallet/dto/CreateRechargeResponse.java` -> `wallet/entity/RechargeOrder`
- `wallet/dto/CreateWithdrawResponse.java` -> `wallet/entity/WithdrawOrder`
- `wallet/dto/CreateTransferResponse.java` -> `wallet/entity/TransferOrder`
- `content/dto/CommentResponse.java` -> `content/entity/Comment`
- `content/dto/UserRecentCommentResponse.java` -> `content/entity/Comment`

这些 DTO 虽然没有直接把 entity 对象交给 Jackson 序列化，但 `from(Entity)` 已经把 HTTP 模型和持久化模型绑在一起。

### 3.2 Service 返回 HTTP DTO

当前 `market` 和 `wallet` 的 controller-facing service 直接返回 response DTO：

- `RechargeService.complete(...) -> CreateRechargeResponse`
- `WithdrawService.request(...) -> CreateWithdrawResponse`
- `TransferService.create(...) -> CreateTransferResponse`
- `MarketOrderService.createOrder(...) -> MarketOrderResponse`
- `MarketOrderService.deliverVirtualOrder(...) -> MarketOrderResponse`
- `MarketQueryService.listBuyingOrders(...) -> List<MarketOrderResponse>`

这让 service 合同变成 HTTP 合同。以后同一用例被 job、listener、内部管理面、跨域 API 复用时，会被迫接受 HTTP response 类型。

### 3.3 Controller 缺少 DTO 适配职责

Controller 当前大多只是：

```java
return Result.ok(applicationService.someAction(...));
```

终态应改为：

```java
SomeResult result = applicationService.someAction(...);
return Result.ok(SomeResponse.from(result));
```

Controller 负责 HTTP 输入输出，`ApplicationService` 负责用例编排并返回应用层模型。

---

## 4. Design Alternatives

### 4.1 方案 A：只把 `from(Entity)` 移到 mapper 工具类

做法：

- 新增 `MarketOrderDtoMapper`
- DTO 不再 import entity
- mapper 工具类继续接收 entity 并返回 DTO
- service 仍返回 HTTP DTO

优点：

- 改动小
- 可以快速清理 DTO import

缺点：

- service 仍依赖 HTTP DTO，根问题没有解决
- HTTP DTO 仍然是业务服务返回合同
- mapper 工具类会变成新的实体到 HTTP 直通层

结论：不采用，只适合临时止血。

### 4.2 方案 B：ApplicationService 返回应用层 result/view model

做法：

- 在 owner 域内部新增应用层输出模型，例如 `market.model.MarketOrderResult`、`wallet.model.RechargeOrderResult`
- service 和 `ApplicationService` 返回这些 result/view model
- DTO 只从 result/view model 构造
- controller 在 HTTP 边界做最后一跳转换
- 查询场景可以由 mapper 直接投影到 result/view model

优点：

- 依赖方向清楚
- 与既有 `ApplicationService` 风格一致
- 不需要引入重型 use case 框架
- API 字段可以稳定，表实体可以独立变化
- 后续 job/listener/internal API 可以复用应用层结果而不依赖 HTTP DTO

缺点：

- 需要批量修改 service 返回类型和测试
- 短期会出现 entity -> result -> DTO 的显式映射代码

结论：采用。

### 4.3 方案 C：全面 CQRS，所有查询都走独立 query handler

做法：

- 为所有读写用例创建 command/result/query/view handler
- controller 不再直接依赖现有 service
- mapper 全面返回 read model

优点：

- 边界最强
- 大型复杂查询扩展性好

缺点：

- 对当前 `community-app` 过重
- 和现有应用层收敛方向冲突
- 迁移成本高，容易产生半成品并行风格

结论：不采用。

---

## 5. Target Design

### 5.1 Layering Rule

目标依赖方向：

```text
controller / dto
        |
        v
ApplicationService
        |
        v
domain service / model
        |
        v
entity / mapper
```

允许：

- controller 依赖本域 `dto` 与本域 `ApplicationService`
- DTO 依赖 Java 标准类型、本域应用层 result/view model、必要的通用 web 类型
- `ApplicationService` 依赖本域 service、本域 model、跨域 owner `api.*`
- service 依赖本域 entity、mapper、model、跨域 owner `api.*`
- mapper 返回 entity 或应用层 read model

禁止：

- `..dto..` 依赖 `..entity..`
- `..dto..` 依赖 `..mapper..` / `..dao..`
- controller-facing `ApplicationService` 返回 HTTP response DTO
- domain service 返回 HTTP response DTO
- foreign domain 直接依赖其他域的 DTO 作为协作模型

### 5.2 Application Result / View Models

新增或扩展 owner 域内部 model：

```text
backend/community-app/src/main/java/com/nowcoder/community/market/model/
backend/community-app/src/main/java/com/nowcoder/community/wallet/model/
backend/community-app/src/main/java/com/nowcoder/community/content/model/
```

命名规则：

- 写操作输出：`*Result`
  - `RechargeOrderResult`
  - `WithdrawOrderResult`
  - `TransferOrderResult`
  - `MarketOrderResult`
  - `MarketDisputeResult`
- 查询输出：`*View` 或 `*Summary`
  - `MarketOrderDetailView`
  - `MarketListingSummary`
  - `MarketInventoryUnitView`
  - `CommentView`
- 嵌套结构也放在 model 中，不放在 DTO 内部直接接 entity
  - `MarketShipmentView`
  - `MarketOrderDeliveryView`

`api.model` 仍然保留给跨域同步协作使用。不要把 HTTP DTO 迁进去，也不要把仅供 controller response 使用的模型放进 `api.model`。

### 5.3 DTO Mapping

DTO 只允许从 application result/view model 构造：

```java
public record CreateRechargeResponse(UUID orderId, String requestId, UUID userId, long amount, String status) {

    public static CreateRechargeResponse from(RechargeOrderResult result) {
        return new CreateRechargeResponse(
                result.orderId(),
                result.requestId(),
                result.userId(),
                result.amount(),
                result.status()
        );
    }
}
```

不允许：

```java
public static CreateRechargeResponse from(RechargeOrder order)
```

对于字段较多的 DTO，可以新增 package-private mapper：

```text
market/dto/MarketOrderResponseMapper.java
```

但 mapper 也只能接收 `market.model.*`，不能接收 entity。

### 5.4 Service Mapping

写服务内部可以继续使用 entity，并在返回前转成 result：

```java
@Transactional
public RechargeOrderResult complete(String requestId, UUID userId, long amount) {
    RechargeOrder order = ...;
    return RechargeOrderResult.from(order);
}
```

这里 `RechargeOrderResult.from(RechargeOrder)` 是允许的，因为 result model 位于 owner 域内部 model 层，用来隔离 HTTP DTO 和持久化实体。它不能放在 `dto` 包中。

更严格的实现也可以用独立 assembler：

```text
wallet/model/RechargeOrderResultMapper.java
```

本设计不强制所有 result 禁止依赖 entity，强制的是 HTTP DTO 和 controller-facing HTTP response 类型不得依赖 entity。

### 5.5 Query Mapping

查询场景优先使用 read model 投影：

```text
MarketOrderMapper.selectByBuyerUserId(...) -> List<MarketOrderSummary>
MarketListingMapper.selectPublicListings(...) -> List<MarketListingSummary>
```

如果某个查询需要复用复杂业务校验，可以先查 entity，再在 service 内转 result/view。判断标准：

- 纯列表/详情读取，字段稳定且不需要实体行为：优先 mapper 投影到 view model
- 写操作后的返回、需要事务内校验的对象：service 内 entity -> result

---

## 6. Migration Plan

### 6.1 Phase 1: Wallet Create Responses

先改低风险、字段少的 wallet 创建响应：

- `CreateRechargeResponse`
- `CreateWithdrawResponse`
- `CreateTransferResponse`

新增：

- `wallet/model/RechargeOrderResult`
- `wallet/model/WithdrawOrderResult`
- `wallet/model/TransferOrderResult`

修改：

- `RechargeService.complete(...)` 返回 `RechargeOrderResult`
- `WithdrawService.request(...)` 返回 `WithdrawOrderResult`
- `TransferService.create(...)` 返回 `TransferOrderResult`
- `WalletApplicationService` 返回 result
- `WalletController` 转成 response DTO
- DTO 删除 entity import

测试：

- wallet service tests 改断言 result
- wallet controller tests 保持 HTTP JSON 断言不变

### 6.2 Phase 2: Market Order Responses

改订单主链路：

- `MarketOrderResponse`
- `MarketOrderDetailResponse`
- `MarketOrderService`
- `MarketQueryService`
- `MarketApplicationService`
- `MarketController`

新增：

- `market/model/MarketOrderResult`
- `market/model/MarketOrderDetailView`
- `market/model/MarketShipmentView`

保持现有 HTTP 字段不变。订单地址快照、配送内容、物流信息属于订单详情 view，不应由 DTO 直接读取 `MarketOrder` 或 `MarketShipment`。

### 6.3 Phase 3: Market Remaining DTOs

继续迁移：

- `MarketListingResponse`
- `MarketListingDetailResponse`
- `MarketAddressResponse`
- `MarketInventoryUnitResponse`
- `MarketDisputeResponse`

按查询/写操作分流：

- 列表和详情查询可以让 mapper 返回 `*Summary` / `*View`
- 写操作后返回可以由 service 内部 entity -> result

### 6.4 Phase 4: Content DTOs

迁移已发现的 content DTO：

- `CommentResponse`
- `UserRecentCommentResponse`

如果这些 DTO 已经和 `content.api.model.CommentView` 等跨域模型重叠，优先复用 owner 域内部应用模型，避免 HTTP DTO 直接依赖 entity。

### 6.5 Phase 5: Architecture Rules

在所有当前违规点清理后，加硬规则：

- `..dto..` must not depend on `..entity..`
- `..dto..` must not depend on `..mapper..` or `..dao..`
- `..service..` must not depend on classes whose simple name ends with `Response` in `..dto..`
- `..service..` must not depend on classes whose simple name ends with `Request` in `..dto..` unless explicitly controller-facing `ApplicationService` and the request is same-domain input DTO during migration

请求 DTO 的长期目标也是解耦：`ApplicationService` 最终接收 command 参数或 application command model，而不是 HTTP request DTO。但本设计的第一优先级是 response/entity 解耦。

---

## 7. ArchUnit Design

新增或扩展 architecture test，例如：

```text
backend/community-app/src/test/java/com/nowcoder/community/app/arch/DtoBoundaryArchTest.java
```

规则：

```java
@ArchTest
static final ArchRule dto_must_not_depend_on_entities =
        noClasses()
                .that().resideInAnyPackage("..dto..")
                .should().dependOnClassesThat().resideInAnyPackage("..entity..");

@ArchTest
static final ArchRule dto_must_not_depend_on_mappers =
        noClasses()
                .that().resideInAnyPackage("..dto..")
                .should().dependOnClassesThat().resideInAnyPackage("..mapper..", "..dao..");
```

Service response rule 需要避免误伤 `Result` 包装类或非 HTTP result。建议用自定义 condition：

- origin package matches `com.nowcoder.community.<domain>.service..`
- target package matches `com.nowcoder.community.<same-domain>.dto..`
- target simple name ends with `Response`
- violation

迁移期间如果必须分阶段落地，可以临时设置显式 whitelist。但 whitelist 必须有空集合断言：

```java
@Test
void dtoEntityBoundaryShouldNotRequireLegacyExceptions() {
    assertThat(LEGACY_DTO_ENTITY_DEPENDENCIES).isEmpty();
}
```

---

## 8. Testing Strategy

### 8.1 Unit Tests

Service tests 改为断言 application result/view model：

- `RechargeServiceTest`
- `WithdrawServiceTest`
- `TransferServiceTest`
- `MarketOrderServiceTest`
- `MarketOrderServiceUnitTest`

Controller tests 继续断言 HTTP response：

- `WalletControllerTest`
- `MarketControllerTest`

这能明确区分业务用例输出和 HTTP 输出。

### 8.2 Architecture Tests

迁移完成后运行：

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=DtoBoundaryArchTest,ControllerBoundaryArchTest test
```

如果只新增到现有 `ControllerBoundaryArchTest`，则运行：

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=ControllerBoundaryArchTest test
```

### 8.3 Focused Regression Tests

每个阶段至少运行对应域测试：

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=WalletControllerTest,RechargeServiceTest,WithdrawServiceTest,TransferServiceTest test
```

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=MarketControllerTest,MarketOrderServiceTest,MarketOrderServiceUnitTest test
```

---

## 9. Acceptance Criteria

完成后必须满足：

- `rg '^import com\\.nowcoder\\.community\\..*\\.entity\\.' backend/community-app/src/main/java/com/nowcoder/community/*/dto` 无结果
- `rg '^import com\\.nowcoder\\.community\\..*\\.mapper\\.' backend/community-app/src/main/java/com/nowcoder/community/*/dto` 无结果
- `market/dto`、`wallet/dto`、`content/dto` 中 response DTO 的 `from(...)` 入参不再是 entity
- `wallet.service` 不再返回 `Create*Response`
- `market.service` 不再返回 `Market*Response`
- controller 保持返回 HTTP DTO
- 现有 HTTP JSON 字段默认不变
- 架构测试覆盖 DTO -> entity/mapper 禁止依赖
- 架构测试覆盖 service -> HTTP response DTO 禁止依赖

---

## 10. Risks And Mitigations

### 10.1 映射代码增加

风险：entity -> result -> DTO 会增加样板代码。

缓解：只在字段较多时增加 mapper/assembler；字段少的 record 可以保留 `from(result)`。

### 10.2 Result Model 变成新的表模型镜像

风险：`*Result` 只是复制 entity 字段，仍然没有表达业务语义。

缓解：result/view model 按 use case 输出定义字段。允许短期保持 HTTP 字段兼容，但新增字段必须先回答“这是 API/业务概念，还是表实现细节”。

### 10.3 迁移期间类型改动影响测试较多

风险：service tests 和 controller tests 同时需要改。

缓解：按域、按 response 类型分阶段迁移。每阶段只处理少量 DTO，并保持 controller JSON 不变。

### 10.4 `dto` request 仍进入 ApplicationService

风险：本次先处理 response/entity 解耦，但 `ApplicationService` 仍可能接收 HTTP request DTO。

缓解：在本 spec 中明确这是下一阶段目标。当前禁止 service 返回 HTTP response DTO；request DTO 到 command model 的迁移可以后续单独执行。

---

## 11. Implementation Order

推荐执行顺序：

1. 新增 `wallet.model` order result records
2. 改 wallet service/application/controller/DTO/tests
3. 新增 DTO boundary ArchUnit 测试，先覆盖 wallet 已清理路径
4. 新增 `market.model` order result/detail view records
5. 改 market order service/query/application/controller/DTO/tests
6. 扩展 ArchUnit 规则到所有 DTO
7. 迁移 market listing/address/inventory/dispute DTO
8. 迁移 content comment DTO
9. 移除所有临时 whitelist
10. 运行 focused tests 和 architecture tests

---

## 12. Final Boundary Statement

本设计完成后，表模型变化最多影响 mapper、entity、service 内部映射和 application result/view model。只要 HTTP 合同没有变化，`dto` 包和 controller response shape 不应被迫跟随表字段变化。

这是本次改造的核心验收标准。
