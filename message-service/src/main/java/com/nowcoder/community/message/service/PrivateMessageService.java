package com.nowcoder.community.message.service;

import com.nowcoder.community.message.dao.MessageMapper;
import com.nowcoder.community.message.api.dto.ConversationItemResponse;
import com.nowcoder.community.message.api.dto.UserSummaryResponse;
import com.nowcoder.community.message.entity.Message;
import com.nowcoder.community.message.service.dto.ConversationStats;
import com.nowcoder.community.message.service.dto.UserSummary;
import com.nowcoder.community.message.projection.UserModerationProjectionRepository;
import com.nowcoder.community.common.security.OwnerGuard;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PrivateMessageService {

    private final MessageMapper messageMapper;
    private final UserServiceClient userServiceClient;
    private final UserModerationProjectionRepository projectionRepository;
    private final UserModerationGuard moderationGuard;
    private final OwnerGuard ownerGuard;

    public PrivateMessageService(
            MessageMapper messageMapper,
            UserServiceClient userServiceClient,
            UserModerationProjectionRepository projectionRepository,
            UserModerationGuard moderationGuard,
            OwnerGuard ownerGuard
    ) {
        this.messageMapper = messageMapper;
        this.userServiceClient = userServiceClient;
        this.projectionRepository = projectionRepository;
        this.moderationGuard = moderationGuard;
        this.ownerGuard = ownerGuard;
    }

    public List<Message> listConversations(int userId, int page, int size) {
        int offset = Math.max(0, page) * Math.max(1, size);
        return messageMapper.selectConversations(userId, offset, size);
    }

    public List<ConversationItemResponse> listConversationItems(int userId, int page, int size) {
        List<Message> latest = listConversations(userId, page, size);
        if (latest == null || latest.isEmpty()) {
            return List.of();
        }

        List<String> conversationIds = latest.stream()
                .map(Message::getConversationId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();

        Map<String, ConversationStats> statsMap = new HashMap<>();
        if (!conversationIds.isEmpty()) {
            List<ConversationStats> stats = messageMapper.selectConversationStats(userId, conversationIds);
            if (stats != null) {
                for (ConversationStats s : stats) {
                    if (s != null && StringUtils.hasText(s.getConversationId())) {
                        statsMap.put(s.getConversationId(), s);
                    }
                }
            }
        }

        Set<Integer> targetIds = latest.stream()
                .map(m -> m.getFromId() == userId ? m.getToId() : m.getFromId())
                .filter(id -> id > 0)
                .collect(Collectors.toSet());
        Map<Integer, UserSummary> userMap = userServiceClient.safeBatchGetUsers(targetIds);

        return latest.stream().map(m -> {
            ConversationItemResponse item = new ConversationItemResponse();
            item.setConversationId(m.getConversationId());
            item.setLastMessage(m);
            ConversationStats s = statsMap.get(m.getConversationId());
            item.setLetterCount(s == null ? 0 : s.getLetterCount());
            item.setUnreadCount(s == null ? 0 : s.getUnreadCount());

            int targetId = m.getFromId() == userId ? m.getToId() : m.getFromId();
            UserSummary target = userMap == null ? null : userMap.get(targetId);
            if (target != null && target.getId() > 0) {
                UserSummaryResponse tu = new UserSummaryResponse();
                tu.setId(target.getId());
                tu.setUsername(target.getUsername());
                tu.setHeaderUrl(target.getHeaderUrl());
                item.setTargetUser(tu);
            }
            return item;
        }).toList();
    }

    public List<Message> listLetters(int userId, String conversationId, int page, int size) {
        ownerGuard.assertConversationMember(userId, conversationId);
        int offset = Math.max(0, page) * Math.max(1, size);
        return messageMapper.selectLetters(userId, conversationId, offset, size);
    }

    public int unreadCount(int userId, String conversationId) {
        return messageMapper.selectLetterUnreadCount(userId, conversationId);
    }

    public void send(int fromId, int toId, String content) {
        moderationGuard.assertCanSendMessage(fromId);
        projectionRepository.assertNotBlocked(fromId, toId);
        Message msg = new Message();
        msg.setFromId(fromId);
        msg.setToId(toId);
        msg.setConversationId(conversationId(fromId, toId));
        msg.setContent(content);
        msg.setStatus(NoticeService.STATUS_UNREAD);
        msg.setCreateTime(new Date());
        messageMapper.insertMessage(msg);
    }

    public void markRead(int userId, List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        messageMapper.updateLettersStatusForRecipient(ids, NoticeService.STATUS_READ, userId);
    }

    private String conversationId(int fromId, int toId) {
        int small = Math.min(fromId, toId);
        int large = Math.max(fromId, toId);
        return small + "_" + large;
    }
}
