package com.nowcoder.community.ops.application;

import java.util.Set;

public interface OutboxHandlerCatalog {

    boolean hasHandler(String topic);

    Set<String> topics();
}
