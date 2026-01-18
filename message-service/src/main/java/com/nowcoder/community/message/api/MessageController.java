package com.nowcoder.community.message.api;

import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.message.api.dto.MarkReadRequest;
import com.nowcoder.community.message.api.dto.SendMessageRequest;
import com.nowcoder.community.message.api.dto.ConversationItemResponse;
import com.nowcoder.community.message.entity.Message;
import com.nowcoder.community.message.service.PrivateMessageService;
import com.nowcoder.community.message.service.UserServiceClient;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import org.springframework.util.StringUtils;

import static com.nowcoder.community.common.api.CommonErrorCode.INVALID_ARGUMENT;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final PrivateMessageService privateMessageService;
    private final UserServiceClient userServiceClient;

    public MessageController(PrivateMessageService privateMessageService, UserServiceClient userServiceClient) {
        this.privateMessageService = privateMessageService;
        this.userServiceClient = userServiceClient;
    }

    @GetMapping("/conversations")
    public Result<List<Message>> conversations(
            Authentication authentication,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        int userId = Integer.parseInt(jwt.getSubject());
        int p = page == null ? 0 : Math.max(0, page);
        int s = size == null ? 10 : Math.min(50, Math.max(1, size));
        return Result.ok(privateMessageService.listConversations(userId, p, s));
    }

    @GetMapping("/conversations/detail")
    public Result<List<ConversationItemResponse>> conversationItems(
            Authentication authentication,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        int userId = Integer.parseInt(jwt.getSubject());
        int p = page == null ? 0 : Math.max(0, page);
        int s = size == null ? 10 : Math.min(50, Math.max(1, size));
        return Result.ok(privateMessageService.listConversationItems(userId, p, s));
    }

    @GetMapping("/conversations/{conversationId}")
    public Result<List<Message>> letters(
            Authentication authentication,
            @PathVariable String conversationId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        int p = page == null ? 0 : Math.max(0, page);
        int s = size == null ? 10 : Math.min(50, Math.max(1, size));
        return Result.ok(privateMessageService.listLetters(conversationId, p, s));
    }

    @GetMapping("/unread-count")
    public Result<Integer> unreadCount(Authentication authentication, @RequestParam(required = false) String conversationId) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        int userId = Integer.parseInt(jwt.getSubject());
        return Result.ok(privateMessageService.unreadCount(userId, conversationId));
    }

    @PostMapping
    public Result<Void> send(Authentication authentication, @Valid @RequestBody SendMessageRequest request) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        int fromId = Integer.parseInt(jwt.getSubject());

        Integer toId = request.getToId();
        String toName = request.getToName();
        if ((toId == null || toId <= 0) && !StringUtils.hasText(toName)) {
            throw new BusinessException(INVALID_ARGUMENT, "toId/toName 至少提供一个");
        }
        if (toId == null || toId <= 0) {
            toId = userServiceClient.safeResolveUserIdByUsername(toName);
            if (toId == null || toId <= 0) {
                throw new BusinessException(INVALID_ARGUMENT, "目标用户不存在");
            }
        }
        privateMessageService.send(fromId, toId, request.getContent());
        return Result.ok();
    }

    @PutMapping("/read")
    public Result<Void> markRead(@Valid @RequestBody MarkReadRequest request) {
        privateMessageService.markRead(request.getIds());
        return Result.ok();
    }
}
