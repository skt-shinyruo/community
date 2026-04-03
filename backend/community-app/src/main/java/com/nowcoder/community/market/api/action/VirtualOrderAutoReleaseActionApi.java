package com.nowcoder.community.market.api.action;

import com.nowcoder.community.market.api.model.VirtualOrderAutoReleaseResult;

public interface VirtualOrderAutoReleaseActionApi {

    VirtualOrderAutoReleaseResult autoReleaseDueOrders();
}
