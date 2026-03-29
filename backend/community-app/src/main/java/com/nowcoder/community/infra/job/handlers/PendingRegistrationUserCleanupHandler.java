package com.nowcoder.community.infra.job.handlers;

import com.nowcoder.community.user.api.action.UserRegistrationActionApi;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class PendingRegistrationUserCleanupHandler {

    static final String JOB_NAME = "pendingRegistrationUserCleanup";

    private static final Logger log = LoggerFactory.getLogger(PendingRegistrationUserCleanupHandler.class);

    private final UserRegistrationActionApi userRegistrationActionApi;
    private final long pendingUserTtlSeconds;

    public PendingRegistrationUserCleanupHandler(
            UserRegistrationActionApi userRegistrationActionApi,
            @Value("${auth.registration.pending-user.ttl-seconds:1800}") long pendingUserTtlSeconds
    ) {
        this.userRegistrationActionApi = userRegistrationActionApi;
        this.pendingUserTtlSeconds = pendingUserTtlSeconds;
    }

    @XxlJob(JOB_NAME)
    public void cleanup() {
        try {
            Duration ttl = Duration.ofSeconds(Math.max(60L, pendingUserTtlSeconds));
            int deleted = userRegistrationActionApi.cleanupExpiredPendingUsers(ttl);
            String result = "[registration] pending-user cleanup deleted-count=" + deleted;
            XxlJobHelper.log(result);
            XxlJobHelper.handleSuccess(result);
            if (deleted > 0) {
                log.info("[registration] cleaned up expired pending users count={}", deleted);
            }
        } catch (RuntimeException e) {
            String message = "[registration] pending-user cleanup failed: " + e;
            XxlJobHelper.log(e);
            XxlJobHelper.handleFail(message);
            log.warn(message);
        }
    }
}
