package com.nowcoder.community.im.realtime.fanout;

import java.net.URI;

public record RealtimeWorkerEndpoint(
        String workerId,
        URI uri
) {
}
