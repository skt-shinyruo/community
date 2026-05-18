package com.nowcoder.community.runtime.application;

import com.nowcoder.community.runtime.application.result.RuntimeConfigResult;
import com.nowcoder.community.runtime.config.RuntimeConfigProperties;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class RuntimeConfigApplicationService {

    private final RuntimeConfigProperties properties;

    public RuntimeConfigApplicationService(RuntimeConfigProperties properties) {
        this.properties = properties;
    }

    public RuntimeConfigResult current() {
        RuntimeConfigProperties.Upload upload = properties.getUpload();
        return new RuntimeConfigResult(
                properties.getApiBasePath(),
                properties.getPublicGatewayOrigin(),
                properties.getWebsocketUrl(),
                properties.isAnalyticsEnabled(),
                properties.getAnalyticsSampleRate(),
                properties.getReleaseChannel(),
                Map.copyOf(properties.getFeatures()),
                new RuntimeConfigResult.UploadPolicy(
                        upload.getMaxFileSize(),
                        upload.getMaxRequestSize(),
                        List.copyOf(upload.getAllowedMimeTypes()),
                        List.copyOf(upload.getAllowedExtensions()),
                        upload.isAvatarUploadEnabled(),
                        upload.isMediaUploadEnabled()
                )
        );
    }
}
