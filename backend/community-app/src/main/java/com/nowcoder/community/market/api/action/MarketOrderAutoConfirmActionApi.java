package com.nowcoder.community.market.api.action;

import com.nowcoder.community.market.api.model.MarketOrderAutoConfirmResult;

public interface MarketOrderAutoConfirmActionApi {

    MarketOrderAutoConfirmResult autoConfirmDueOrders();
}
