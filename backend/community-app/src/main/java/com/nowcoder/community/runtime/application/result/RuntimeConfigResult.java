package com.nowcoder.community.runtime.application.result;

import java.util.Map;

public record RuntimeConfigResult(
        String apiBasePath,
        String publicGatewayOrigin,
        String websocketUrl,
        boolean analyticsEnabled,
        double analyticsSampleRate,
        String releaseChannel,
        Map<String, Boolean> features
) {
}
