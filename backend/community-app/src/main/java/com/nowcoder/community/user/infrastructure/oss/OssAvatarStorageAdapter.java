package com.nowcoder.community.user.infrastructure.oss;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.oss.client.CommunityOssClient;
import com.nowcoder.community.oss.client.model.OssCompleteUploadRequest;
import com.nowcoder.community.oss.client.model.OssMetadataResponse;
import com.nowcoder.community.oss.client.model.OssUploadSessionRequest;
import com.nowcoder.community.oss.client.model.OssUploadSessionResponse;
import com.nowcoder.community.user.application.AvatarUploadContent;
import com.nowcoder.community.user.application.port.AvatarStoragePort;
import com.nowcoder.community.user.application.result.AvatarUploadTokenResult;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.List;

import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.exception.CommonErrorCode.INTERNAL_ERROR;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@Component
public class OssAvatarStorageAdapter implements AvatarStoragePort {

    private static final String OWNER_KEY_PREFIX = "user:avatar:oss-owner:";
    private static final String SESSION_KEY_PREFIX = "user:avatar:oss-session:";
    private static final String PUBLIC_URL_KEY_PREFIX = "user:avatar:oss-public-url:";
    private static final long UPLOAD_TICKET_TTL_SECONDS = 600;
    private static final String KEY_PREFIX = "avatar/";
    private static final long MAX_AVATAR_BYTES = 2L * 1024 * 1024;
    private static final String MIME_LIMIT = "image/jpeg;image/png;image/webp;image/gif";
    private static final List<String> ALLOWED_MIME_TYPES = List.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif"
    );

    private final CommunityOssClient ossClient;
    private final StringRedisTemplate redisTemplate;
    private final OssAvatarProperties properties;

    public OssAvatarStorageAdapter(
            CommunityOssClient ossClient,
            StringRedisTemplate redisTemplate,
            OssAvatarProperties properties
    ) {
        this.ossClient = ossClient;
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public AvatarUploadTokenResult createUploadToken(UUID userId) {
        if (userId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }

        String fileKey = generateFileName(userId);
        OssUploadSessionResponse response = ossClient.prepareUpload(new OssUploadSessionRequest(
                "USER_AVATAR",
                "community-app",
                "user",
                "avatar",
                userId.toString(),
                "PUBLIC",
                "avatar.png",
                "application/octet-stream",
                0,
                "",
                userId.toString()
        ));
        if (response == null) {
            throw new BusinessException(INTERNAL_ERROR, "签发上传参数失败");
        }

        bindUploadTicket(userId, fileKey);
        bindUploadSession(fileKey, response);
        return new AvatarUploadTokenResult(
                response.sessionId() == null ? "" : response.sessionId().toString(),
                fileKey,
                "/api/users/" + userId + "/avatar/upload",
                "POST",
                "file",
                "fileKey",
                MAX_AVATAR_BYTES,
                MIME_LIMIT,
                response.expiresAt()
        );
    }

    @Override
    public void upload(UUID userId, String fileKey, AvatarUploadContent content) {
        if (userId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        if (!isSafeFileName(fileKey)) {
            throw new BusinessException(INVALID_ARGUMENT, "fileKey 非法");
        }
        validateContent(content);
        requireUploadOwner(userId, fileKey, false);

        UploadSessionReference sessionReference = requireUploadSession(fileKey);
        OssMetadataResponse metadata;
        try {
            metadata = ossClient.completeProxyUpload(new OssCompleteUploadRequest(
                    sessionReference.sessionId(),
                    sessionReference.objectId(),
                    sessionReference.versionId(),
                    content::openStream,
                    "avatar.png",
                    content.contentType(),
                    content.size(),
                    ""
            ));
        } catch (RuntimeException e) {
            throw new BusinessException(INTERNAL_ERROR, "上传头像失败", e);
        }

        if (metadata == null) {
            throw new BusinessException(INTERNAL_ERROR, "上传头像失败");
        }
        if (!StringUtils.hasText(metadata.publicUrl())) {
            throw new BusinessException(INTERNAL_ERROR, "上传头像失败");
        }
        cachePublicUrl(fileKey, metadata.publicUrl());
    }

    @Override
    public void assertAndConsumeUploadTicket(UUID userId, String fileKey) {
        if (userId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        if (!isSafeFileName(fileKey)) {
            throw new BusinessException(INVALID_ARGUMENT, "fileKey 非法");
        }
        if (redisTemplate == null) {
            throw new BusinessException(FORBIDDEN, "上传校验不可用");
        }
        String owner = redisTemplate.opsForValue().getAndDelete(ownerKey(fileKey));
        if (!StringUtils.hasText(owner) || !owner.trim().equals(userId.toString())) {
            throw new BusinessException(FORBIDDEN, "上传凭证已失效或不匹配");
        }
    }

    @Override
    public String buildAvatarUrl(String fileKey) {
        if (!isSafeFileName(fileKey)) {
            throw new BusinessException(INVALID_ARGUMENT, "fileKey 非法");
        }
        String cached = redisTemplate == null ? null : redisTemplate.opsForValue().get(publicUrlKey(fileKey));
        if (StringUtils.hasText(cached)) {
            return cached.trim();
        }
        throw new BusinessException(INTERNAL_ERROR, "头像公共地址不可用");
    }

    private void bindUploadTicket(UUID userId, String fileName) {
        if (redisTemplate == null) {
            throw new BusinessException(INVALID_ARGUMENT, "Redis 未配置，无法签发上传凭证");
        }
        redisTemplate.opsForValue().set(ownerKey(fileName), userId.toString(), UPLOAD_TICKET_TTL_SECONDS, TimeUnit.SECONDS);
    }

    private void bindUploadSession(String fileName, OssUploadSessionResponse response) {
        if (redisTemplate == null) {
            throw new BusinessException(INVALID_ARGUMENT, "Redis 未配置，无法签发上传凭证");
        }
        redisTemplate.opsForValue().set(sessionKey(fileName), encodeSession(response), UPLOAD_TICKET_TTL_SECONDS, TimeUnit.SECONDS);
    }

    private void cachePublicUrl(String fileName, String publicUrl) {
        if (redisTemplate == null) {
            return;
        }
        redisTemplate.opsForValue().set(publicUrlKey(fileName), publicUrl.trim(), UPLOAD_TICKET_TTL_SECONDS, TimeUnit.SECONDS);
    }

    private void requireUploadOwner(UUID userId, String fileName, boolean consume) {
        if (redisTemplate == null) {
            throw new BusinessException(FORBIDDEN, "上传校验不可用");
        }
        String owner = consume
                ? redisTemplate.opsForValue().getAndDelete(ownerKey(fileName))
                : redisTemplate.opsForValue().get(ownerKey(fileName));
        if (!StringUtils.hasText(owner) || !owner.trim().equals(userId.toString())) {
            throw new BusinessException(FORBIDDEN, "上传凭证已失效或不匹配");
        }
    }

    private UploadSessionReference requireUploadSession(String fileName) {
        if (redisTemplate == null) {
            throw new BusinessException(FORBIDDEN, "上传校验不可用");
        }
        String encoded = redisTemplate.opsForValue().get(sessionKey(fileName));
        if (!StringUtils.hasText(encoded)) {
            throw new BusinessException(FORBIDDEN, "上传凭证已失效或不匹配");
        }
        String[] parts = encoded.trim().split("\\|", -1);
        if (parts.length != 3) {
            throw new BusinessException(INVALID_ARGUMENT, "上传凭证格式非法");
        }
        return new UploadSessionReference(
                UUID.fromString(parts[0]),
                UUID.fromString(parts[1]),
                UUID.fromString(parts[2])
        );
    }

    private String encodeSession(OssUploadSessionResponse response) {
        return response.sessionId() + "|" + response.objectId() + "|" + response.versionId();
    }

    private String ownerKey(String fileName) {
        return OWNER_KEY_PREFIX + fileName.trim();
    }

    private String sessionKey(String fileName) {
        return SESSION_KEY_PREFIX + fileName.trim();
    }

    private String publicUrlKey(String fileName) {
        return PUBLIC_URL_KEY_PREFIX + fileName.trim();
    }

    private String generateFileName(UUID userId) {
        return KEY_PREFIX + userId + "/" + UUID.randomUUID().toString().replace("-", "");
    }

    private boolean isSafeFileName(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return false;
        }
        String normalized = fileName.trim();
        return normalized.startsWith(KEY_PREFIX)
                && normalized.length() <= 200
                && !normalized.contains("..")
                && !normalized.contains("\\")
                && !normalized.contains("\u0000");
    }

    private void validateContent(AvatarUploadContent content) {
        if (content == null || content.empty()) {
            throw new BusinessException(INVALID_ARGUMENT, "文件不能为空");
        }
        if (content.size() > MAX_AVATAR_BYTES) {
            throw new BusinessException(INVALID_ARGUMENT, "头像文件过大（maxBytes=" + MAX_AVATAR_BYTES + "）");
        }
        String contentType = content.contentType();
        if (!ALLOWED_MIME_TYPES.contains(contentType)) {
            throw new BusinessException(INVALID_ARGUMENT, "不支持的图片格式（mime=" + contentType + "）");
        }
    }

    private record UploadSessionReference(UUID sessionId, UUID objectId, UUID versionId) {
    }
}
