package com.nowcoder.community.im.realtime.fanout;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public class RoomFanoutPlanner {

    public List<RoomFanoutRoute> plan(UUID roomId, long lastSeq, Set<String> activeWorkerIds) {
        if (roomId == null || lastSeq <= 0 || activeWorkerIds == null || activeWorkerIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalizedWorkerIds = new LinkedHashSet<>();
        for (String workerId : activeWorkerIds) {
            if (StringUtils.hasText(workerId)) {
                normalizedWorkerIds.add(workerId.trim());
            }
        }
        if (normalizedWorkerIds.isEmpty()) {
            return List.of();
        }
        ArrayList<RoomFanoutRoute> routes = new ArrayList<>(normalizedWorkerIds.size());
        for (String workerId : normalizedWorkerIds) {
            routes.add(new RoomFanoutRoute(roomId, lastSeq, workerId));
        }
        return List.copyOf(routes);
    }
}
