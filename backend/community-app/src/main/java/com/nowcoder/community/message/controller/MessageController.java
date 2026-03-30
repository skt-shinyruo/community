package com.nowcoder.community.message.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.infra.idempotency.IdempotencyGuard;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import com.nowcoder.community.message.app.ListConversationItemsQuery;
import com.nowcoder.community.message.app.ListLettersQuery;
import com.nowcoder.community.message.app.MarkMessagesReadUseCase;
import com.nowcoder.community.message.app.SendPrivateMessageUseCase;
import com.nowcoder.community.message.dto.ConversationItemResponse;
import com.nowcoder.community.message.dto.LetterItemResponse;
import com.nowcoder.community.message.dto.MarkReadRequest;
import com.nowcoder.community.message.dto.SendMessageRequest;
import com.nowcoder.community.message.service.PrivateMessageService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
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

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final PrivateMessageService privateMessageService;
    private final ListConversationItemsQuery listConversationItemsQuery;
    private final ListLettersQuery listLettersQuery;
    private final SendPrivateMessageUseCase sendPrivateMessageUseCase;
    private final MarkMessagesReadUseCase markMessagesReadUseCase;

    public MessageController(
            PrivateMessageService privateMessageService,
            ListConversationItemsQuery listConversationItemsQuery,
            ListLettersQuery listLettersQuery,
            SendPrivateMessageUseCase sendPrivateMessageUseCase,
            MarkMessagesReadUseCase markMessagesReadUseCase
    ) {
        this.privateMessageService = privateMessageService;
        this.listConversationItemsQuery = listConversationItemsQuery;
        this.listLettersQuery = listLettersQuery;
        this.sendPrivateMessageUseCase = sendPrivateMessageUseCase;
        this.markMessagesReadUseCase = markMessagesReadUseCase;
    }

    @GetMapping("/conversations")
    public Result<List<LetterItemResponse>> conversations(
            Authentication authentication,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return Result.ok(listConversationItemsQuery.listConversations(authentication, page, size));
    }

    @GetMapping("/conversations/detail")
    public Result<List<ConversationItemResponse>> conversationItems(
            Authentication authentication,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return Result.ok(listConversationItemsQuery.listConversationItems(authentication, page, size));
    }

    @GetMapping("/conversations/{conversationId}")
    public Result<List<LetterItemResponse>> letters(
            Authentication authentication,
            @PathVariable String conversationId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return Result.ok(listLettersQuery.listLetters(authentication, conversationId, page, size));
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
        sendPrivateMessageUseCase.send(authentication, idempotencyKey, request);
        return Result.ok();
    }

    @PutMapping("/read")
    public Result<Void> markRead(Authentication authentication, @Valid @RequestBody MarkReadRequest request) {
        markMessagesReadUseCase.markRead(authentication, request);
        return Result.ok();
    }
}
