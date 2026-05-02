package com.nowcoder.community.market.application;

import com.nowcoder.community.market.application.command.AddMarketInventoryBatchCommand;
import com.nowcoder.community.market.application.command.CreateMarketAddressCommand;
import com.nowcoder.community.market.application.command.CreateMarketListingCommand;
import com.nowcoder.community.market.controller.dto.AddMarketInventoryBatchRequest;
import com.nowcoder.community.market.controller.dto.CreateMarketAddressRequest;
import com.nowcoder.community.market.controller.dto.CreateMarketListingRequest;

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
                request.getGoodsType(),
                request.getTitle(),
                request.getDescription(),
                request.getUnitPrice(),
                request.getDeliveryMode(),
                request.getStockMode(),
                request.getStockTotal(),
                request.getMinPurchaseQuantity(),
                request.getMaxPurchaseQuantity(),
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
        return new AddMarketInventoryBatchCommand(listingId, sellerUserId, request.getPayloadType(), request.getPayloads());
    }

    public static CreateMarketAddressCommand addressCommand(UUID userId, CreateMarketAddressRequest request) {
        return new CreateMarketAddressCommand(
                userId,
                request.getReceiverName(),
                request.getReceiverPhone(),
                request.getProvince(),
                request.getCity(),
                request.getDistrict(),
                request.getDetailAddress(),
                request.getPostalCode(),
                request.isDefaultAddress()
        );
    }
}
