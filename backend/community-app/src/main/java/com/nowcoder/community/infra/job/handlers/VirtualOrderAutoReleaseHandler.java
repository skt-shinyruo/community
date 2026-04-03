package com.nowcoder.community.infra.job.handlers;

import com.nowcoder.community.market.api.action.VirtualOrderAutoReleaseActionApi;
import com.nowcoder.community.market.api.model.VirtualOrderAutoReleaseResult;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class VirtualOrderAutoReleaseHandler {

    static final String JOB_NAME = "virtualOrderAutoRelease";

    private static final Logger log = LoggerFactory.getLogger(VirtualOrderAutoReleaseHandler.class);

    private final VirtualOrderAutoReleaseActionApi virtualOrderAutoReleaseActionApi;

    public VirtualOrderAutoReleaseHandler(VirtualOrderAutoReleaseActionApi virtualOrderAutoReleaseActionApi) {
        this.virtualOrderAutoReleaseActionApi = virtualOrderAutoReleaseActionApi;
    }

    @XxlJob(JOB_NAME)
    public void autoRelease() {
        try {
            VirtualOrderAutoReleaseResult result = virtualOrderAutoReleaseActionApi.autoReleaseDueOrders();
            String message = "[market] auto-release completed=" + result.completedCount() + " skipped=" + result.skippedCount();
            XxlJobHelper.log(message);
            XxlJobHelper.handleSuccess(message);
            log.info(message);
        } catch (RuntimeException e) {
            String message = "[market] auto-release failed: " + e;
            XxlJobHelper.log(e);
            XxlJobHelper.handleFail(message);
            log.warn(message);
        }
    }
}
