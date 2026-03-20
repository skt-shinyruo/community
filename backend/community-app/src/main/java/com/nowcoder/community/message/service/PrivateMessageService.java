package com.nowcoder.community.message.service;

import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.message.exception.MessageErrorCode;
import com.nowcoder.community.message.mapper.MessageMapper;
import com.nowcoder.community.message.dto.ConversationItemResponse;
import com.nowcoder.community.message.dto.LetterItemResponse;
import com.nowcoder.community.message.dto.UserSummaryResponse;
import com.nowcoder.community.message.entity.Message;
import com.nowcoder.community.message.security.OwnerGuard;
import com.nowcoder.community.message.service.dto.ConversationStats;
import com.nowcoder.community.social.block.BlockService;
import com.nowcoder.community.user.exception.UserErrorCode;
import com.nowcoder.community.user.dto.UserSummary;
import com.nowcoder.community.infra.pagination.Pagination;
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
    private final UserLookupService userLookupService;
    private final BlockService blockService;
    private final UserModerationGuard moderationGuard;
    private final OwnerGuard ownerGuard;

    public PrivateMessageService(
            MessageMapper messageMapper,
            UserLookupService userLookupService,
            BlockService blockService,
            UserModerationGuard moderationGuard,
            OwnerGuard ownerGuard
    ) {
        this.messageMapper = messageMapper;
        this.userLookupService = userLookupService;
        this.blockService = blockService;
        this.moderationGuard = moderationGuard;
        this.ownerGuard = ownerGuard;
    }

    public List<Message> listConversations(int userId, int page, int size) {
        int p = Math.max(0, page);
        int s = Math.min(50, Math.max(1, size));
        int offset = Pagination.safeOffset(p, s);
        return messageMapper.selectConversations(userId, offset, s);
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
        Map<Integer, UserSummary> userMap = userLookupService.safeBatchGetUsers(targetIds);

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
        int p = Math.max(0, page);
        int s = Math.min(50, Math.max(1, size));
        int offset = Pagination.safeOffset(p, s);
        return messageMapper.selectLetters(userId, conversationId, offset, s);
    }

    public int unreadCount(int userId, String conversationId) {
        return messageMapper.selectLetterUnreadCount(userId, conversationId);
    }

    public void send(int fromId, int toId, String content) {
        assertValidRecipient(fromId, toId);
        moderationGuard.assertCanSendMessage(fromId);
        if (blockService != null && blockService.isEitherBlocked(fromId, toId)) {
            throw new BusinessException(
                    CommonErrorCode.FORBIDDEN,
                    "双方存在拉黑关系，无法发送私信"
            );
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

    private void assertValidRecipient(int fromId, int toId) {
        if (toId <= 0) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "toId 非法");
        }
        if (fromId == toId) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "不能给自己发送私信");
        }
        UserSummary target = userLookupService.safeGetUser(toId);
        if (target == null || target.getId() <= 0) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND, "目标用户不存在");
        }
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
