package com.nowcoder.community.runtime.application;

import com.nowcoder.community.runtime.config.RuntimeConfigProperties;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class RuntimeConfigApplicationService {

    private final RuntimeConfigProperties properties;

    public RuntimeConfigApplicationService(RuntimeConfigProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    public RuntimeConfig current() {
        RuntimeConfigProperties.Upload upload = properties.getUpload();
        return new RuntimeConfig(
                properties.getApiBasePath(),
                properties.getPublicGatewayOrigin(),
                properties.getWebsocketUrl(),
                properties.isAnalyticsEnabled(),
                properties.getAnalyticsSampleRate(),
                properties.getReleaseChannel(),
                Map.copyOf(properties.getFeatures()),
                new RuntimeConfig.UploadPolicy(
                        upload.getMaxFileSize(),
                        upload.getMaxRequestSize(),
                        List.copyOf(upload.getAllowedMimeTypes()),
                        List.copyOf(upload.getAllowedExtensions()),
                        upload.isAvatarUploadEnabled(),
                        upload.isMediaUploadEnabled()
                )
        );
    }

    public record RuntimeConfig(
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
}
