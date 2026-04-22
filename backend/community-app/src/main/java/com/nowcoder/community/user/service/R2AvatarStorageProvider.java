package com.nowcoder.community.user.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.dto.AvatarUploadTokenResponse;
import com.nowcoder.community.user.config.AvatarStorageProperties;
import com.nowcoder.community.user.config.R2Properties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.InputStream;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INTERNAL_ERROR;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

/**
 * Cloudflare R2 implementation (S3 compatible).
 *
 * <p>Upload is server-side multipart (same as local). Read path is served via {@code /files/**} and streams from R2.</p>
 */
@Service
@ConditionalOnProperty(name = "user.avatar.storage", havingValue = "r2")
@EnableConfigurationProperties({R2Properties.class, AvatarStorageProperties.class})
public class R2AvatarStorageProvider implements AvatarStorageProvider {

    private final R2Properties r2Properties;
    private final AvatarStorageProperties avatarStorageProperties;
    private final S3Client s3Client;

    public R2AvatarStorageProvider(R2Properties r2Properties, AvatarStorageProperties avatarStorageProperties, S3Client s3Client) {
        this.r2Properties = r2Properties;
        this.avatarStorageProperties = avatarStorageProperties;
        this.s3Client = s3Client;
    }

    @Override
    public String provider() {
        return "r2";
    }

    @Override
    public AvatarUploadTokenResponse createUploadToken(UUID userId, String fileName) {
        AvatarUploadTokenResponse resp = new AvatarUploadTokenResponse();
        resp.setProvider(provider());
        resp.setFileName(fileName);
        resp.setUploadMethod("POST");
        resp.setUploadUrl("/api/users/" + userId + "/avatar/upload");
        return resp;
    }

    @Override
    public void upload(UUID userId, String fileName, MultipartFile file) {
        if (userId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(INVALID_ARGUMENT, "文件不能为空");
        }
        if (file.getSize() > AvatarConstraints.MAX_AVATAR_BYTES) {
            throw new BusinessException(INVALID_ARGUMENT, "头像文件过大（maxBytes=" + AvatarConstraints.MAX_AVATAR_BYTES + "）");
        }
        String contentType = StringUtils.hasText(file.getContentType()) ? file.getContentType().trim().toLowerCase() : "";
        if (!AvatarConstraints.ALLOWED_MIME_TYPES.contains(contentType)) {
            throw new BusinessException(INVALID_ARGUMENT, "不支持的图片格式（mime=" + contentType + "）");
        }
        if (!StringUtils.hasText(fileName) || !fileName.startsWith(AvatarConstraints.KEY_PREFIX + userId + "/")) {
            throw new BusinessException(INVALID_ARGUMENT, "fileName 非法");
        }

        String bucket = requireBucketName();
        String key = fileName.trim();
        try (InputStream in = file.getInputStream()) {
            PutObjectRequest req = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .build();
            s3Client.putObject(req, RequestBody.fromInputStream(in, file.getSize()));
        } catch (S3Exception e) {
            throw new BusinessException(INTERNAL_ERROR, "上传头像失败", e);
        } catch (java.io.IOException | RuntimeException e) {
            throw new BusinessException(INTERNAL_ERROR, "上传头像失败", e);
        }
    }

    @Override
    public String buildAvatarUrl(String fileName) {
        String base = avatarStorageProperties == null ? "" : avatarStorageProperties.getPublicBaseUrl();
        if (!StringUtils.hasText(base)) {
            throw new BusinessException(INVALID_ARGUMENT, "publicBaseUrl 未配置");
        }
        if (!StringUtils.hasText(fileName) || !fileName.startsWith(AvatarConstraints.KEY_PREFIX)) {
            throw new BusinessException(INVALID_ARGUMENT, "fileName 非法");
        }

        String normalized = base.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized + "/files/" + fileName.trim();
    }

    @Override
    public StoredAvatar loadOrNull(String key) {
        if (!StringUtils.hasText(key)) {
            return null;
        }
        String bucket = requireBucketName();
        String objectKey = key.trim();

        GetObjectRequest req = GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();

        try {
            ResponseInputStream<GetObjectResponse> in = s3Client.getObject(req);
            GetObjectResponse resp = in.response();
            MediaType mediaType = parseMediaTypeOrOctetStream(resp == null ? "" : resp.contentType());
            return new StoredAvatar(new InputStreamResource(in), mediaType);
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return null;
            }
            throw new BusinessException(INTERNAL_ERROR, "读取头像失败", e);
        } catch (RuntimeException e) {
            throw new BusinessException(INTERNAL_ERROR, "读取头像失败", e);
        }
    }

    private String requireBucketName() {
        String bucket = r2Properties == null ? "" : r2Properties.getBucketName();
        if (!StringUtils.hasText(bucket)) {
            throw new BusinessException(INVALID_ARGUMENT, "r2.bucket-name 未配置");
        }
        return bucket.trim();
    }

    private MediaType parseMediaTypeOrOctetStream(String raw) {
        if (!StringUtils.hasText(raw)) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(raw.trim());
        } catch (RuntimeException ignored) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
