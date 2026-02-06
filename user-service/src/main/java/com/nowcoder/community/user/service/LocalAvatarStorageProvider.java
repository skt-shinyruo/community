package com.nowcoder.community.user.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.api.dto.AvatarUploadTokenResponse;
import com.nowcoder.community.user.config.AvatarStorageProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.nowcoder.community.common.api.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.common.api.CommonErrorCode.INTERNAL_ERROR;

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
    public AvatarUploadTokenResponse createUploadToken(int userId, String fileName) {
        AvatarUploadTokenResponse resp = new AvatarUploadTokenResponse();
        resp.setProvider(provider());
        resp.setFileName(fileName);
        resp.setUploadMethod("POST");
        resp.setUploadUrl("/api/users/" + userId + "/avatar/upload");
        return resp;
    }

    @Override
    public void upload(int userId, String fileName, MultipartFile file) {
        if (userId <= 0) {
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

            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (java.io.IOException | RuntimeException e) {
            throw new BusinessException(INTERNAL_ERROR, "保存头像失败");
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
}
