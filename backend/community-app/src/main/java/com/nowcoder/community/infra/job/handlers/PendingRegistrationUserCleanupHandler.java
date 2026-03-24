package com.nowcoder.community.infra.job.handlers;

import com.nowcoder.community.auth.config.RegistrationProperties;
import com.nowcoder.community.user.service.InternalUserService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class PendingRegistrationUserCleanupHandler {

    static final String JOB_NAME = "pendingRegistrationUserCleanup";

    private static final Logger log = LoggerFactory.getLogger(PendingRegistrationUserCleanupHandler.class);

    private final InternalUserService internalUserService;
    private final RegistrationProperties properties;

    public PendingRegistrationUserCleanupHandler(InternalUserService internalUserService,
                                                 RegistrationProperties properties) {
        this.internalUserService = internalUserService;
        this.properties = properties;
    }

    @XxlJob(JOB_NAME)
    public void cleanup() {
        try {
            Duration ttl = Duration.ofSeconds(Math.max(60, properties.getPendingUser().getTtlSeconds()));
            int deleted = internalUserService.cleanupExpiredPendingUsers(ttl);
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
