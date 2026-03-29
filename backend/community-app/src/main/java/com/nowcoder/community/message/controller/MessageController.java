package com.nowcoder.community.message.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.infra.idempotency.IdempotencyGuard;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import com.nowcoder.community.message.dto.LetterItemResponse;
import com.nowcoder.community.message.dto.MarkReadRequest;
import com.nowcoder.community.message.dto.SendMessageRequest;
import com.nowcoder.community.message.dto.ConversationItemResponse;
import com.nowcoder.community.message.service.MessageUserQueryService;
import com.nowcoder.community.message.service.PrivateMessageService;
import com.nowcoder.community.user.exception.UserErrorCode;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final PrivateMessageService privateMessageService;
    private final MessageUserQueryService messageUserQueryService;
    private final IdempotencyGuard idempotencyGuard;

    public MessageController(
            PrivateMessageService privateMessageService,
            MessageUserQueryService messageUserQueryService,
            IdempotencyGuard idempotencyGuard
    ) {
        this.privateMessageService = privateMessageService;
        this.messageUserQueryService = messageUserQueryService;
        this.idempotencyGuard = idempotencyGuard;
    }

    @GetMapping("/conversations")
    public Result<List<LetterItemResponse>> conversations(
            Authentication authentication,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        int userId = CurrentUser.requireUserId(authentication);
        int p = page == null ? 0 : Math.max(0, page);
        int s = size == null ? 10 : Math.min(50, Math.max(1, size));
        return Result.ok(privateMessageService.listConversationSummaries(userId, p, s));
    }

    @GetMapping("/conversations/detail")
    public Result<List<ConversationItemResponse>> conversationItems(
            Authentication authentication,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        int userId = CurrentUser.requireUserId(authentication);
        int p = page == null ? 0 : Math.max(0, page);
        int s = size == null ? 10 : Math.min(50, Math.max(1, size));
        return Result.ok(privateMessageService.listConversationItems(userId, p, s));
    }

    @GetMapping("/conversations/{conversationId}")
    public Result<List<LetterItemResponse>> letters(
            Authentication authentication,
            @PathVariable String conversationId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        int userId = CurrentUser.requireUserId(authentication);
        int p = page == null ? 0 : Math.max(0, page);
        int s = size == null ? 10 : Math.min(50, Math.max(1, size));
        return Result.ok(privateMessageService.listLetterItems(userId, conversationId, p, s));
    }

    @GetMapping("/unread-count")
    public Result<Integer> unreadCount(Authentication authentication, @RequestParam(required = false) String conversationId) {
        int userId = CurrentUser.requireUserId(authentication);
        return Result.ok(privateMessageService.unreadCount(userId, conversationId));
    }

    @PostMapping
    public Result<Void> send(
            Authentication authentication,
            @RequestHeader(value = IdempotencyGuard.HEADER_IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody SendMessageRequest request
    ) {
        int fromId = CurrentUser.requireUserId(authentication);

        Integer toId = request.getToId();
        String toName = request.getToName();
        if ((toId == null || toId <= 0) && !StringUtils.hasText(toName)) {
            throw new BusinessException(INVALID_ARGUMENT, "toId/toName 至少提供一个");
        }
        if (toId == null || toId <= 0) {
            toId = messageUserQueryService.findUserIdByUsernameOrNull(toName);
            if (toId == null || toId <= 0) {
                throw new BusinessException(UserErrorCode.USER_NOT_FOUND, "目标用户不存在");
            }
        } else if (messageUserQueryService.findUserSummaryByIdOrNull(toId) == null) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND, "目标用户不存在");
        }
        int resolvedToId = toId;
        String content = request.getContent();
        idempotencyGuard.executeRequired("message:send_message", fromId, idempotencyKey, Void.class, () -> {
            privateMessageService.send(fromId, resolvedToId, content);
            return null;
        });
        return Result.ok();
    }

    @PutMapping("/read")
    public Result<Void> markRead(Authentication authentication, @Valid @RequestBody MarkReadRequest request) {
        int userId = CurrentUser.requireUserId(authentication);
        privateMessageService.markRead(userId, request.getIds());
        return Result.ok();
    }
}
