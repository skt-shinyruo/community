package com.nowcoder.community.im.controller;

import com.nowcoder.community.im.application.ImPrivateMessageDecisionApplicationService;
import com.nowcoder.community.im.common.policy.PrivateMessagePolicyDecision;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/internal/im/realtime/projections")
public class ImPrivateMessageDecisionController {

    private final ImPrivateMessageDecisionApplicationService decisionService;

    public ImPrivateMessageDecisionController(ImPrivateMessageDecisionApplicationService decisionService) {
        this.decisionService = decisionService;
    }

    @GetMapping("/private-message-decision")
    public ResponseEntity<PrivateMessagePolicyDecision> privateMessageDecision(
            @RequestParam("fromUserId") UUID fromUserId,
            @RequestParam("toUserId") UUID toUserId
    ) {
        try {
            return ResponseEntity.ok(decisionService.decidePrivateMessage(fromUserId, toUserId));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }
}
