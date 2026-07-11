package com.nowcoder.community.im.realtime.fanout;

public record RealtimeWorkerEndpoint(String workerId, int roomFanoutInboxSlot) {
}
