package com.nowcoder.community.content.infrastructure.oss;

import com.nowcoder.community.oss.client.HttpCommunityOssClient;
import com.nowcoder.community.content.application.PostMediaReferenceQueryPort;
import com.nowcoder.community.oss.client.model.OssReferenceResponse;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Component
public class OssPostMediaReferenceQueryAdapter implements PostMediaReferenceQueryPort {

    private final HttpCommunityOssClient ossClient;

    public OssPostMediaReferenceQueryAdapter(HttpCommunityOssClient ossClient) {
        this.ossClient = Objects.requireNonNull(ossClient, "ossClient must not be null");
    }

    @Override
    public RemoteReferenceStatus findReferenceStatus(UUID objectId, UUID referenceId) {
        OssReferenceResponse response = ossClient.getObjectReference(objectId, referenceId);
        if (response == null) {
            return RemoteReferenceStatus.MISSING;
        }
        if (response.status() == null) {
            return RemoteReferenceStatus.UNKNOWN;
        }
        return switch (response.status().trim().toUpperCase(Locale.ROOT)) {
            case "ACTIVE" -> RemoteReferenceStatus.ACTIVE;
            case "RELEASED" -> RemoteReferenceStatus.RELEASED;
            default -> RemoteReferenceStatus.UNKNOWN;
        };
    }
}
