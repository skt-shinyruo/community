package com.nowcoder.community.message.service;

import com.nowcoder.community.infra.pagination.Pagination;
import com.nowcoder.community.message.dto.ConversationItemResponse;
import com.nowcoder.community.message.dto.LetterItemResponse;
import com.nowcoder.community.message.dto.UserSummaryResponse;
import com.nowcoder.community.message.entity.Message;
import com.nowcoder.community.message.exception.MessageErrorCode;
import com.nowcoder.community.message.mapper.MessageMapper;
import com.nowcoder.community.message.security.OwnerGuard;
import com.nowcoder.community.message.service.dto.ConversationStats;
import com.nowcoder.community.user.api.model.UserSummaryView;
import com.nowcoder.community.user.api.query.UserLookupQueryApi;
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
    private final UserLookupQueryApi userLookupQueryApi;
    private final PrivateMessageGovernanceService governanceService;
    private final OwnerGuard ownerGuard;
    private final MessageItemAssembler messageItemAssembler;

    public PrivateMessageService(
            MessageMapper messageMapper,
            UserLookupQueryApi userLookupQueryApi,
            PrivateMessageGovernanceService governanceService,
            OwnerGuard ownerGuard,
            MessageItemAssembler messageItemAssembler
    ) {
        this.messageMapper = messageMapper;
        this.userLookupQueryApi = userLookupQueryApi;
        this.governanceService = governanceService;
        this.ownerGuard = ownerGuard;
        this.messageItemAssembler = messageItemAssembler;
    }

    public List<Message> listConversations(int userId, int page, int size) {
        int p = Math.max(0, page);
        int s = Math.min(50, Math.max(1, size));
        int offset = Pagination.safeOffset(p, s);
        return messageMapper.selectConversations(userId, offset, s);
    }

    public List<LetterItemResponse> listConversationSummaries(int userId, int page, int size) {
        List<Message> latest = listConversations(userId, page, size);
        if (latest == null || latest.isEmpty()) {
            return List.of();
        }
        return latest.stream().map(messageItemAssembler::toLetterItem).toList();
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

        Map<Integer, UserSummaryResponse> userMap = new HashMap<>();
        if (userLookupQueryApi != null && !targetIds.isEmpty()) {
            List<UserSummaryView> userSummaries = userLookupQueryApi.listSummariesByIds(targetIds.stream().toList());
            if (userSummaries != null) {
                for (UserSummaryView userSummary : userSummaries) {
                    if (userSummary == null || userSummary.id() <= 0) {
                        continue;
                    }
                    UserSummaryResponse response = new UserSummaryResponse();
                    response.setId(userSummary.id());
                    response.setUsername(userSummary.username());
                    response.setHeaderUrl(userSummary.headerUrl());
                    userMap.put(userSummary.id(), response);
                }
            }
        }

        return latest.stream().map(m -> {
            ConversationItemResponse item = new ConversationItemResponse();
            item.setConversationId(m.getConversationId());
            item.setLastMessage(messageItemAssembler.toLetterItem(m));
            ConversationStats s = statsMap.get(m.getConversationId());
            item.setLetterCount(s == null ? 0 : s.getLetterCount());
            item.setUnreadCount(s == null ? 0 : s.getUnreadCount());

            int targetId = m.getFromId() == userId ? m.getToId() : m.getFromId();
            UserSummaryResponse targetUser = userMap.get(targetId);
            if (targetUser != null && targetUser.getId() > 0) {
                item.setTargetUser(targetUser);
            }
            return item;
        }).toList();
    }

    public List<Message> listLetters(int userId, String conversationId, int page, int size) {
        ownerGuard.assertConversationMember(userId, conversationId, MessageErrorCode.CONVERSATION_NOT_FOUND);
        int p = Math.max(0, page);
        int s = Math.min(50, Math.max(1, size));
        int offset = Pagination.safeOffset(p, s);
        return messageMapper.selectLetters(userId, conversationId, offset, s);
    }

    public List<LetterItemResponse> listLetterItems(int userId, String conversationId, int page, int size) {
        List<Message> letters = listLetters(userId, conversationId, page, size);
        if (letters == null || letters.isEmpty()) {
            return List.of();
        }
        return letters.stream().map(messageItemAssembler::toLetterItem).toList();
    }

    public int unreadCount(int userId, String conversationId) {
        return messageMapper.selectLetterUnreadCount(userId, conversationId);
    }

    public void send(int fromId, int toId, String content) {
        governanceService.validateCanSendPrivateMessage(fromId, toId);
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
