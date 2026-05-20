package com.nowcoder.community.im.realtime.fanout;

import com.nowcoder.community.im.common.command.RoomFanoutCommand;
import com.nowcoder.community.im.realtime.push.RoomFanoutCoalescer;
import com.nowcoder.community.im.realtime.session.ImSessionProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
public class RoomFanoutTargetService {

    private static final int MAX_RECENT_SOURCE_EVENTS = 100_000;

    private final RoomFanoutCoalescer roomFanoutCoalescer;
    private final String localWorkerId;
    private final ConcurrentHashMap<String, Boolean> acceptedSourceEventIds = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<String> acceptedSourceEventOrder = new ConcurrentLinkedQueue<>();

    public RoomFanoutTargetService(
            RoomFanoutCoalescer roomFanoutCoalescer,
            ImSessionProperties sessionProperties
    ) {
        this.roomFanoutCoalescer = roomFanoutCoalescer;
        this.localWorkerId = normalize(sessionProperties == null ? null : sessionProperties.getWorkerId(), "local");
    }

    public RoomFanoutTargetResult apply(RoomFanoutCommand command) {
        if (!isValid(command)) {
            return RoomFanoutTargetResult.INVALID;
        }
        String targetWorkerId = normalize(command.targetWorkerId(), "");
        if (!localWorkerId.equals(targetWorkerId)) {
            return RoomFanoutTargetResult.WRONG_TARGET;
        }
        String sourceEventId = command.sourceEventId().trim();
        if (acceptedSourceEventIds.putIfAbsent(sourceEventId, Boolean.TRUE) != null) {
            return RoomFanoutTargetResult.DUPLICATE;
        }
        acceptedSourceEventOrder.add(sourceEventId);
        evictOldSourceEventIds();
        roomFanoutCoalescer.markRoomUpdated(command.roomId(), command.lastSeq());
        return RoomFanoutTargetResult.ACCEPTED;
    }

    private static boolean isValid(RoomFanoutCommand command) {
        return command != null
                && StringUtils.hasText(command.targetWorkerId())
                && command.roomId() != null
                && command.lastSeq() > 0
                && StringUtils.hasText(command.sourceEventId());
    }

    private static String normalize(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private void evictOldSourceEventIds() {
        int overflow = acceptedSourceEventIds.size() - MAX_RECENT_SOURCE_EVENTS;
        for (int i = 0; i < overflow; i++) {
            String sourceEventId = acceptedSourceEventOrder.poll();
            if (sourceEventId == null) {
                return;
            }
            acceptedSourceEventIds.remove(sourceEventId);
        }
        if (acceptedSourceEventOrder.size() <= MAX_RECENT_SOURCE_EVENTS * 2) {
            return;
        }
        Iterator<String> iterator = acceptedSourceEventOrder.iterator();
        while (iterator.hasNext()) {
            String sourceEventId = iterator.next();
            if (!acceptedSourceEventIds.containsKey(sourceEventId)) {
                iterator.remove();
            }
        }
    }
}
