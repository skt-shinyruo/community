package com.nowcoder.community.user.infrastructure.oss;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.oss.client.CommunityOssClient;
import com.nowcoder.community.oss.client.model.OssMetadataResponse;
import com.nowcoder.community.oss.client.model.OssUploadSessionRequest;
import com.nowcoder.community.oss.client.model.OssUploadSessionResponse;
import com.nowcoder.community.user.application.command.CreateAvatarUploadSessionCommand;
import com.nowcoder.community.user.application.port.AvatarStoragePort;
import com.nowcoder.community.user.application.result.AvatarUploadSessionResult;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.exception.CommonErrorCode.INTERNAL_ERROR;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@Component
public class OssAvatarStorageAdapter implements AvatarStoragePort {

    private static final String USAGE = "USER_AVATAR";
    private static final String OWNER_SERVICE = "community-app";
    private static final String OWNER_DOMAIN = "user";
    private static final String OWNER_TYPE = "avatar";
    private static final String VISIBILITY_PUBLIC = "PUBLIC";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String UPLOAD_METHOD = "POST";
    private static final String FILE_FIELD = "file";
    private static final long MAX_AVATAR_BYTES = 2L * 1024 * 1024;
    private static final List<String> ALLOWED_MIME_TYPES = List.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif"
    );

    private final CommunityOssClient ossClient;

    public OssAvatarStorageAdapter(CommunityOssClient ossClient) {
        this.ossClient = ossClient;
    }

    @Override
    public AvatarUploadSessionResult createUploadSession(UUID userId, CreateAvatarUploadSessionCommand command) {
        if (userId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        validateCommand(command);

        OssUploadSessionResponse response = ossClient.prepareUpload(new OssUploadSessionRequest(
                USAGE,
                OWNER_SERVICE,
                OWNER_DOMAIN,
                OWNER_TYPE,
                userId.toString(),
                VISIBILITY_PUBLIC,
                safeFileName(command.fileName()),
                command.contentType(),
                command.contentLength(),
                command.checksumSha256(),
                userId.toString()
        ));
        if (response == null || response.sessionId() == null || response.objectId() == null || response.versionId() == null) {
            throw new BusinessException(INTERNAL_ERROR, "签发头像上传会话失败");
        }
        if (!"PROXY".equalsIgnoreCase(response.uploadMode())) {
            throw new BusinessException(INTERNAL_ERROR, "不支持的头像上传模式");
        }

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("sessionId", response.sessionId().toString());
        fields.put("versionId", response.versionId().toString());
        if (StringUtils.hasText(command.checksumSha256())) {
            fields.put("checksumSha256", command.checksumSha256());
        }

        return new AvatarUploadSessionResult(
                response.sessionId().toString(),
                response.objectId(),
                response.versionId(),
                response.uploadUrl(),
                UPLOAD_METHOD,
                FILE_FIELD,
                fields,
                Map.of(),
                MAX_AVATAR_BYTES,
                ALLOWED_MIME_TYPES,
                response.expiresAt()
        );
    }

    @Override
    public String resolvePublicAvatarUrl(UUID userId, UUID objectId) {
        if (userId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        if (objectId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "objectId 非法");
        }

        OssMetadataResponse metadata = ossClient.getMetadata(objectId);
        if (metadata == null) {
            throw new BusinessException(FORBIDDEN, "头像对象不存在或不可用");
        }
        requireOwnedAvatar(userId, metadata);
        if (!StringUtils.hasText(metadata.publicUrl())) {
            throw new BusinessException(INTERNAL_ERROR, "头像公共地址不可用");
        }
        return metadata.publicUrl().trim();
    }

    private static void validateCommand(CreateAvatarUploadSessionCommand command) {
        if (command == null) {
            throw new BusinessException(INVALID_ARGUMENT, "上传会话参数非法");
        }
        if (!StringUtils.hasText(command.fileName())) {
            throw new BusinessException(INVALID_ARGUMENT, "文件名不能为空");
        }
        if (!ALLOWED_MIME_TYPES.contains(command.contentType())) {
            throw new BusinessException(INVALID_ARGUMENT, "不支持的图片格式（mime=" + command.contentType() + "）");
        }
        if (command.contentLength() <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "文件不能为空");
        }
        if (command.contentLength() > MAX_AVATAR_BYTES) {
            throw new BusinessException(INVALID_ARGUMENT, "头像文件过大（maxBytes=" + MAX_AVATAR_BYTES + "）");
        }
    }

    private static String safeFileName(String fileName) {
        String normalized = fileName == null ? "" : fileName.trim();
        int slash = Math.max(normalized.lastIndexOf('/'), normalized.lastIndexOf('\\'));
        if (slash >= 0) {
            normalized = normalized.substring(slash + 1);
        }
        if (!StringUtils.hasText(normalized) || normalized.contains("\u0000") || normalized.length() > 120) {
            throw new BusinessException(INVALID_ARGUMENT, "文件名非法");
        }
        return normalized;
    }

    private static void requireOwnedAvatar(UUID userId, OssMetadataResponse metadata) {
        if (!USAGE.equals(metadata.usage())
                || !OWNER_SERVICE.equals(metadata.ownerService())
                || !OWNER_DOMAIN.equals(metadata.ownerDomain())
                || !OWNER_TYPE.equals(metadata.ownerType())
                || !userId.toString().equals(metadata.ownerId())
                || !VISIBILITY_PUBLIC.equals(metadata.visibility())
                || !STATUS_ACTIVE.equals(metadata.status())) {
            throw new BusinessException(FORBIDDEN, "头像对象不存在或不可用");
        }
    }
}
