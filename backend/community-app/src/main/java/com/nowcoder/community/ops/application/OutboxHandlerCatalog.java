package com.nowcoder.community.ops.application;

public interface OutboxHandlerCatalog {

    boolean hasHandler(String topic);
}
