package com.nowcoder.community.gateway.shard;

import java.util.Optional;

public interface ShardRouter {

    Optional<WorkerDescriptor> route(String userId);
}
