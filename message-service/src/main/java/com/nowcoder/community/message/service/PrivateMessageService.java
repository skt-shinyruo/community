package com.nowcoder.community.message.service;

import com.nowcoder.community.message.api.MessageErrorCode;
import com.nowcoder.community.message.dao.MessageMapper;
import com.nowcoder.community.message.api.dto.ConversationItemResponse;
import com.nowcoder.community.message.api.dto.LetterItemResponse;
import com.nowcoder.community.message.api.dto.UserSummaryResponse;
import com.nowcoder.community.message.entity.Message;
import com.nowcoder.community.message.service.dto.ConversationStats;
import com.nowcoder.community.message.projection.UserModerationProjectionRepository;
import com.nowcoder.community.common.security.OwnerGuard;
import com.nowcoder.community.user.api.rpc.dto.UserSummary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
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
    private final SocialServiceClient socialServiceClient;
    private final UserModerationProjectionRepository projectionRepository;
    private final UserModerationGuard moderationGuard;
    private final OwnerGuard ownerGuard;

    public PrivateMessageService(
            MessageMapper messageMapper,
            UserServiceClient userServiceClient,
            SocialServiceClient socialServiceClient,
            UserModerationProjectionRepository projectionRepository,
            UserModerationGuard moderationGuard,
            OwnerGuard ownerGuard
    ) {
        this.messageMapper = messageMapper;
        this.userServiceClient = userServiceClient;
        this.socialServiceClient = socialServiceClient;
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
            item.setLastMessage(toLetterItem(m));
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
        ownerGuard.assertConversationMember(userId, conversationId, MessageErrorCode.CONVERSATION_NOT_FOUND);
        int offset = Math.max(0, page) * Math.max(1, size);
        return messageMapper.selectLetters(userId, conversationId, offset, size);
    }

    public int unreadCount(int userId, String conversationId) {
        return messageMapper.selectLetterUnreadCount(userId, conversationId);
    }

    public void send(int fromId, int toId, String content) {
        moderationGuard.assertCanSendMessage(fromId);
        UserModerationProjectionRepository.BlockCheck check = projectionRepository.checkEitherBlocked(fromId, toId);
        if (check == UserModerationProjectionRepository.BlockCheck.BLOCKED) {
            throw new com.nowcoder.community.common.exception.BusinessException(
                    com.nowcoder.community.common.api.CommonErrorCode.FORBIDDEN,
                    "双方存在拉黑关系，无法发送私信"
            );
        }
        if (check == UserModerationProjectionRepository.BlockCheck.UNKNOWN) {
            // 冷启动/漏消息/滞后：回源 social-service SSOT（internal）避免 fail-open，并回填投影减少后续回源
            boolean blocked = Boolean.TRUE.equals(socialServiceClient.isEitherBlocked(fromId, toId));
            Instant now = Instant.now();
            projectionRepository.upsertBlockRelation(fromId, toId, blocked, now);
            projectionRepository.upsertBlockRelation(toId, fromId, blocked, now);
            if (blocked) {
                throw new com.nowcoder.community.common.exception.BusinessException(
                        com.nowcoder.community.common.api.CommonErrorCode.FORBIDDEN,
                        "双方存在拉黑关系，无法发送私信"
                );
            }
        }
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

    private LetterItemResponse toLetterItem(Message m) {
        if (m == null) {
            return null;
        }
        LetterItemResponse r = new LetterItemResponse();
        r.setId(m.getId());
        r.setFromId(m.getFromId());
        r.setToId(m.getToId());
        r.setConversationId(m.getConversationId());
        r.setContent(m.getContent());
        r.setStatus(m.getStatus());
        r.setCreateTime(m.getCreateTime());
        return r;
    }
}
