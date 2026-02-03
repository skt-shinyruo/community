package com.nowcoder.community.user.outbox;

import com.nowcoder.community.common.api.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Outbox 运维接口（internal-token 保护由 common 的 InternalTokenFilter 统一兜底）。
 */
@RestController
@RequestMapping("/internal/users/outbox")
public class InternalOutboxController {

    private final OutboxEventService outboxEventService;

    public InternalOutboxController(OutboxEventService outboxEventService) {
        this.outboxEventService = outboxEventService;
    }

    @GetMapping("/health")
    public Result<OutboxHealthResponse> health() {
        OutboxHealthResponse resp = new OutboxHealthResponse();
        resp.setNewCount(outboxEventService.countByStatus("NEW"));
        resp.setRetryCount(outboxEventService.countByStatus("RETRY"));
        resp.setSendingCount(outboxEventService.countByStatus("SENDING"));
        resp.setFailedCount(outboxEventService.countByStatus("FAILED"));
        return Result.ok(resp);
    }

    @PostMapping("/replay")
    public Result<Integer> replayFailed(@RequestParam(required = false) Integer limit) {
        int n = outboxEventService.replayFailed(limit == null ? 200 : limit);
        return Result.ok(n);
    }

    public static class OutboxHealthResponse {
        private int newCount;
        private int retryCount;
        private int sendingCount;
        private int failedCount;

        public int getNewCount() {
            return newCount;
        }

        public void setNewCount(int newCount) {
            this.newCount = newCount;
        }

        public int getRetryCount() {
            return retryCount;
        }

        public void setRetryCount(int retryCount) {
            this.retryCount = retryCount;
        }

        public int getSendingCount() {
            return sendingCount;
        }

        public void setSendingCount(int sendingCount) {
            this.sendingCount = sendingCount;
        }

        public int getFailedCount() {
            return failedCount;
        }

        public void setFailedCount(int failedCount) {
            this.failedCount = failedCount;
        }
    }
}

