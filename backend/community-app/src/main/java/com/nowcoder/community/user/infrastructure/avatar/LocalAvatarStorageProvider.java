package com.nowcoder.community.user.infrastructure.avatar;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.application.AvatarUploadContent;
import com.nowcoder.community.user.application.result.AvatarUploadTokenResult;
import com.nowcoder.community.user.config.AvatarStorageProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.common.exception.CommonErrorCode.INTERNAL_ERROR;

@Service
@EnableConfigurationProperties(AvatarStorageProperties.class)
public class LocalAvatarStorageProvider implements AvatarStorageProvider {

    private final AvatarStorageProperties properties;

    public LocalAvatarStorageProvider(AvatarStorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public String provider() {
        return "local";
    }

    @Override
    public AvatarUploadTokenResult createUploadToken(UUID userId, String fileName) {
        return new AvatarUploadTokenResult(
                provider(),
                null,
                fileName,
                null,
                "/api/users/" + userId + "/avatar/upload",
                "POST",
                0,
                null
        );
    }

    @Override
    public void upload(UUID userId, String fileName, AvatarUploadContent content) {
        if (userId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        if (content == null || content.empty()) {
            throw new BusinessException(INVALID_ARGUMENT, "文件不能为空");
        }
        if (content.size() > AvatarConstraints.MAX_AVATAR_BYTES) {
            throw new BusinessException(INVALID_ARGUMENT, "头像文件过大（maxBytes=" + AvatarConstraints.MAX_AVATAR_BYTES + "）");
        }
        String contentType = content.contentType();
        if (!AvatarConstraints.ALLOWED_MIME_TYPES.contains(contentType)) {
            throw new BusinessException(INVALID_ARGUMENT, "不支持的图片格式（mime=" + contentType + "）");
        }
        if (!StringUtils.hasText(fileName) || !fileName.startsWith(AvatarConstraints.KEY_PREFIX + userId + "/")) {
            throw new BusinessException(INVALID_ARGUMENT, "fileName 非法");
        }

        String baseDir = properties.getFilesBaseDir();
        if (!StringUtils.hasText(baseDir)) {
            throw new BusinessException(INVALID_ARGUMENT, "filesBaseDir 未配置");
        }

        try {
            Path base = Paths.get(baseDir).toAbsolutePath().normalize();
            Files.createDirectories(base);

            Path target = base.resolve(fileName).normalize();
            if (!target.startsWith(base)) {
                throw new BusinessException(INVALID_ARGUMENT, "fileName 非法");
            }

            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (Files.exists(target)) {
                throw new BusinessException(INVALID_ARGUMENT, "文件已存在，请重试");
            }

            try (InputStream in = content.openStream()) {
                Files.copy(in, target);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (java.io.IOException | RuntimeException e) {
            throw new BusinessException(INTERNAL_ERROR, "保存头像失败", e);
        }
    }

    @Override
    public String buildAvatarUrl(String fileName) {
        String base = properties.getPublicBaseUrl();
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
        return normalized + "/files/" + fileName;
    }

    @Override
    public StoredAvatar loadOrNull(String key) {
        if (!StringUtils.hasText(key)) {
            return null;
        }

        String baseDir = properties.getFilesBaseDir();
        if (!StringUtils.hasText(baseDir)) {
            throw new BusinessException(INVALID_ARGUMENT, "filesBaseDir 未配置");
        }

        Path base = Paths.get(baseDir).toAbsolutePath().normalize();
        Path target = base.resolve(key.trim()).normalize();
        if (!target.startsWith(base)) {
            throw new BusinessException(INVALID_ARGUMENT, "fileKey 非法");
        }
        if (!Files.exists(target) || !Files.isRegularFile(target)) {
            return null;
        }

        MediaType mediaType = detectMediaType(target);
        return new StoredAvatar(new FileSystemResource(target), mediaType);
    }

    private MediaType detectMediaType(Path target) {
        if (target == null) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try (InputStream in = Files.newInputStream(target)) {
            byte[] head = in.readNBytes(16);
            if (isJpeg(head)) {
                return MediaType.IMAGE_JPEG;
            }
            if (isPng(head)) {
                return MediaType.IMAGE_PNG;
            }
            if (isGif(head)) {
                return MediaType.IMAGE_GIF;
            }
            if (isWebp(head)) {
                return MediaType.valueOf("image/webp");
            }
        } catch (java.io.IOException ignored) {
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private boolean isJpeg(byte[] head) {
        return head != null && head.length >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF;
    }

    private boolean isPng(byte[] head) {
        return head != null && head.length >= 8
                && (head[0] & 0xFF) == 0x89
                && head[1] == 0x50
                && head[2] == 0x4E
                && head[3] == 0x47
                && head[4] == 0x0D
                && head[5] == 0x0A
                && head[6] == 0x1A
                && head[7] == 0x0A;
    }

    private boolean isGif(byte[] head) {
        return head != null && head.length >= 6
                && head[0] == 'G'
                && head[1] == 'I'
                && head[2] == 'F'
                && head[3] == '8'
                && (head[4] == '7' || head[4] == '9')
                && head[5] == 'a';
    }

    private boolean isWebp(byte[] head) {
        return head != null && head.length >= 12
                && head[0] == 'R'
                && head[1] == 'I'
                && head[2] == 'F'
                && head[3] == 'F'
                && head[8] == 'W'
                && head[9] == 'E'
                && head[10] == 'B'
                && head[11] == 'P';
    }
}
