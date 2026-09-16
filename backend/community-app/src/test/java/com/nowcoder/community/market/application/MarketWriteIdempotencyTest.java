package com.nowcoder.community.market.application;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.market.application.MarketInventoryApplicationService.AppendInventoryResult;
import com.nowcoder.community.market.application.MarketInventoryApplicationService.MarketInventoryUnitResult;
import com.nowcoder.community.market.application.command.AddMarketInventoryBatchCommand;
import com.nowcoder.community.market.controller.dto.AddMarketInventoryBatchRequest;
import com.nowcoder.community.market.controller.dto.CreateMarketListingRequest;
import com.nowcoder.community.market.exception.MarketErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 写契约回归：库存追加 / 预库存商品发布在“服务端已提交但响应失败”（超时、网关 5xx）后，
 * 客户端携带同一 Idempotency-Key 重试不得产生重复库存或重复商品。
 */
@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class MarketWriteIdempotencyTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MarketListingApplicationService marketListingService;

    @Autowired
    private MarketInventoryApplicationService marketInventoryService;

    @Autowired
    private MarketQueryApplicationService marketQueryService;


    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from market_shipment");
        jdbcTemplate.update("delete from market_dispute");
        jdbcTemplate.update("delete from market_order");
        jdbcTemplate.update("delete from market_inventory_unit");
        jdbcTemplate.update("delete from market_address");
        jdbcTemplate.update("delete from market_listing");
        jdbcTemplate.update("delete from http_idempotency");
    }

    @Test
    void appendInventoryRetryWithSameKeyAfterCommitShouldNotDuplicateUnits() {
        UUID sellerUserId = uuid(7);
        UUID listingId = createPreloadedListing(sellerUserId);
        AddMarketInventoryBatchCommand command = new AddMarketInventoryBatchCommand(
                listingId,
                sellerUserId,
                "CODE",
                List.of("CODE-A", "CODE-B"),
                "it-append-retry-1"
        );

        AppendInventoryResult first = marketInventoryService.appendInventory(command);
        // 服务端已提交但响应失败后的重试：同一 key + 同一 payload。
        AppendInventoryResult replay = marketInventoryService.appendInventory(command);

        assertThat(first.appended()).isEqualTo(2);
        assertThat(replay.appended()).isEqualTo(2);
        assertThat(marketInventoryService.listInventory(listingId, sellerUserId, null, null).items())
                .extracting(MarketInventoryUnitResult::payloadContent)
                .containsExactlyInAnyOrder("initial-code", "CODE-A", "CODE-B");
        assertThat(marketQueryService.getListingDetail(listingId).stockAvailable()).isEqualTo(3);
    }

    @Test
    void appendInventoryReplayWithDifferentPayloadsShouldConflictWithoutWriting() {
        UUID sellerUserId = uuid(7);
        UUID listingId = createPreloadedListing(sellerUserId);
        marketInventoryService.appendInventory(new AddMarketInventoryBatchCommand(
                listingId,
                sellerUserId,
                "CODE",
                List.of("CODE-A"),
                "it-append-conflict-1"
        ));

        assertThatThrownBy(() -> marketInventoryService.appendInventory(new AddMarketInventoryBatchCommand(
                listingId,
                sellerUserId,
                "CODE",
                List.of("CODE-X"),
                "it-append-conflict-1"
        )))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(MarketErrorCode.REQUEST_REPLAY_CONFLICT));

        assertThat(marketInventoryService.listInventory(listingId, sellerUserId, null, null).items())
                .extracting(MarketInventoryUnitResult::payloadContent)
                .containsExactlyInAnyOrder("initial-code", "CODE-A");
        assertThat(marketQueryService.getListingDetail(listingId).stockAvailable()).isEqualTo(2);
    }

    @Test
    void appendInventoryReplayWithInteriorNewlinePayloadShouldConflictRatherThanDedupe() {
        UUID sellerUserId = uuid(7);
        UUID listingId = createPreloadedListing(sellerUserId);
        marketInventoryService.appendInventory(new AddMarketInventoryBatchCommand(
                listingId,
                sellerUserId,
                "TEXT",
                List.of("line-a\nline-b"),
                "it-append-fingerprint-1"
        ));

        // 一条内含换行的 payload 与两条独立 payload 是不同的业务批次，同 key 重放必须 409 而不是静默去重。
        assertThatThrownBy(() -> marketInventoryService.appendInventory(new AddMarketInventoryBatchCommand(
                listingId,
                sellerUserId,
                "TEXT",
                List.of("line-a", "line-b"),
                "it-append-fingerprint-1"
        )))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(MarketErrorCode.REQUEST_REPLAY_CONFLICT));

        assertThat(marketInventoryService.listInventory(listingId, sellerUserId, null, null).items()).hasSize(2);
    }

    @Test
    void appendInventoryShouldRejectMissingIdempotencyKey() {
        UUID sellerUserId = uuid(7);
        UUID listingId = createPreloadedListing(sellerUserId);

        assertThatThrownBy(() -> marketInventoryService.appendInventory(new AddMarketInventoryBatchCommand(
                listingId,
                sellerUserId,
                "CODE",
                List.of("CODE-A"),
                null
        )))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_ARGUMENT));

        assertThat(marketInventoryService.listInventory(listingId, sellerUserId, null, null).items()).hasSize(1);
    }

    @Test
    void createListingRetryWithSameKeyAfterCommitShouldNotDuplicateListingOrInventory() {
        UUID sellerUserId = uuid(7);
        CreateMarketListingRequest request = preloadedListingRequest();
        var command = MarketTestCommands.listingCommand(sellerUserId, request, request.inventory(), "it-create-retry-1");

        var first = marketListingService.createListing(command);
        // 服务端已提交但响应失败后的重试：同一 key + 同一 payload。
        var replay = marketListingService.createListing(command);

        assertThat(replay.listingId()).isEqualTo(first.listingId());
        assertThat(marketQueryService.listSellerListings(sellerUserId, null, null).items()).hasSize(1);
        assertThat(marketInventoryService.listInventory(first.listingId(), sellerUserId, null, null).items())
                .extracting(MarketInventoryUnitResult::payloadContent)
                .containsExactlyInAnyOrder("CODE-1", "CODE-2");
        assertThat(marketQueryService.getListingDetail(first.listingId()).stockAvailable()).isEqualTo(2);
    }

    @Test
    void createListingReplayWithDifferentPayloadShouldConflictWithoutWriting() {
        UUID sellerUserId = uuid(7);
        CreateMarketListingRequest request = preloadedListingRequest();
        UUID listingId = marketListingService.createListing(
                MarketTestCommands.listingCommand(sellerUserId, request, request.inventory(), "it-create-conflict-1"))
                .listingId();

        CreateMarketListingRequest modified = new CreateMarketListingRequest(
                "VIRTUAL", "另一个兑换码商品", "两个预加载兑换码", 100L, "PRELOADED", "FINITE", 2, 1, 1,
                new AddMarketInventoryBatchRequest("CODE", List.of("CODE-9", "CODE-10")));
        assertThatThrownBy(() -> marketListingService.createListing(
                MarketTestCommands.listingCommand(sellerUserId, modified, modified.inventory(), "it-create-conflict-1")))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(MarketErrorCode.REQUEST_REPLAY_CONFLICT));

        assertThat(marketQueryService.listSellerListings(sellerUserId, null, null).items()).hasSize(1);
        assertThat(marketInventoryService.listInventory(listingId, sellerUserId, null, null).items())
                .extracting(MarketInventoryUnitResult::payloadContent)
                .containsExactlyInAnyOrder("CODE-1", "CODE-2");
    }

    @Test
    void createListingShouldRejectMissingIdempotencyKey() {
        UUID sellerUserId = uuid(7);
        CreateMarketListingRequest request = preloadedListingRequest();

        assertThatThrownBy(() -> marketListingService.createListing(
                MarketTestCommands.listingCommand(sellerUserId, request, request.inventory(), null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_ARGUMENT));

        assertThat(marketQueryService.listSellerListings(sellerUserId, null, null).items()).isEmpty();
    }

    private CreateMarketListingRequest preloadedListingRequest() {
        return new CreateMarketListingRequest(
                "VIRTUAL", "兑换码", "两个预加载兑换码", 100L, "PRELOADED", "FINITE", 2, 1, 1,
                new AddMarketInventoryBatchRequest("CODE", List.of("CODE-1", "CODE-2")));
    }

    private UUID createPreloadedListing(UUID sellerUserId) {
        CreateMarketListingRequest request = new CreateMarketListingRequest(
                "VIRTUAL", "兑换码", "单个预加载兑换码", 100L, "PRELOADED", "FINITE", 1, 1, 1,
                new AddMarketInventoryBatchRequest("TEXT", List.of("initial-code")));
        return marketListingService.createListing(
                MarketTestCommands.listingCommand(sellerUserId, request, request.inventory())
        ).listingId();
    }
}
