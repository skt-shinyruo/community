package com.nowcoder.community.runtime.application.result;

import java.util.List;
import java.util.Map;

public record RuntimeConfigResult(
        String apiBasePath,
        String publicGatewayOrigin,
        String websocketUrl,
        boolean analyticsEnabled,
        double analyticsSampleRate,
        String releaseChannel,
        Map<String, Boolean> features,
        UploadPolicy upload
) {

    public record UploadPolicy(
            String maxFileSize,
            String maxRequestSize,
            List<String> allowedMimeTypes,
            List<String> allowedExtensions,
            boolean avatarUploadEnabled,
            boolean mediaUploadEnabled
    ) {
    }
}
