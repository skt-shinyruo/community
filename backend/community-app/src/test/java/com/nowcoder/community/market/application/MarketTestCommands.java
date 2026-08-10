package com.nowcoder.community.market.application;

import com.nowcoder.community.market.application.command.AddMarketInventoryBatchCommand;
import com.nowcoder.community.market.controller.dto.AddMarketInventoryBatchRequest;
import com.nowcoder.community.market.controller.dto.CreateMarketAddressRequest;
import com.nowcoder.community.market.controller.dto.CreateMarketListingRequest;
import com.nowcoder.community.market.application.MarketAddressApplicationService.CreateMarketAddressCommand;
import com.nowcoder.community.market.application.MarketListingApplicationService.CreateMarketListingCommand;

import java.util.UUID;

public final class MarketTestCommands {

    private MarketTestCommands() {
    }

    public static CreateMarketListingCommand listingCommand(
            UUID sellerUserId,
            CreateMarketListingRequest request,
            AddMarketInventoryBatchRequest inventoryRequest
    ) {
        return new CreateMarketListingCommand(
                sellerUserId,
                request.goodsType(),
                request.title(),
                request.description(),
                request.unitPrice(),
                request.deliveryMode(),
                request.stockMode(),
                request.stockTotal(),
                request.minPurchaseQuantity(),
                request.maxPurchaseQuantity(),
                inventoryCommand(null, sellerUserId, inventoryRequest)
        );
    }

    public static AddMarketInventoryBatchCommand inventoryCommand(
            UUID listingId,
            UUID sellerUserId,
            AddMarketInventoryBatchRequest request
    ) {
        if (request == null) {
            return null;
        }
        return new AddMarketInventoryBatchCommand(listingId, sellerUserId, request.payloadType(), request.payloads());
    }

    public static CreateMarketAddressCommand addressCommand(UUID userId, CreateMarketAddressRequest request) {
        return new CreateMarketAddressCommand(
                userId,
                request.receiverName(),
                request.receiverPhone(),
                request.province(),
                request.city(),
                request.district(),
                request.detailAddress(),
                request.postalCode(),
                request.defaultAddress()
        );
    }
}
