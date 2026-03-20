package com.nowcoder.community.user.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.dto.AvatarUploadTokenResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.TimeUnit;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@Service
public class AvatarService {

    private static final String KEY_PREFIX_UPLOAD_TICKET = "user:avatar:upload:";
    private static final long UPLOAD_TICKET_TTL_SECONDS = 600;

    private final AvatarStorageRouter storageRouter;
    private final StringRedisTemplate redisTemplate;

    public AvatarService(AvatarStorageRouter storageRouter, StringRedisTemplate redisTemplate) {
        this.storageRouter = storageRouter;
        this.redisTemplate = redisTemplate;
    }

    public AvatarUploadTokenResponse createUploadToken(int userId) {
        if (userId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }

        String fileName = generateFileName(userId);
        AvatarUploadTokenResponse resp = currentProvider().createUploadToken(userId, fileName);
        if (resp == null) {
            throw new BusinessException(INVALID_ARGUMENT, "签发上传参数失败");
        }
        resp.setFileName(fileName);
        resp.setMaxBytes(AvatarConstraints.MAX_AVATAR_BYTES);
        resp.setMimeLimit(AvatarConstraints.MIME_LIMIT);
        bindUploadTicket(userId, fileName);
        return resp;
    }

    public void upload(int userId, String fileName, MultipartFile file) {
        assertUploadTicketOwner(userId, fileName);
        currentProvider().upload(userId, fileName.trim(), file);
    }

    public void assertAndConsumeUploadTicket(int userId, String fileName) {
        if (userId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        if (!isSafeFileName(fileName)) {
            throw new BusinessException(INVALID_ARGUMENT, "fileName 非法");
        }
        if (redisTemplate == null) {
            throw new BusinessException(FORBIDDEN, "上传校验不可用");
        }
        String key = KEY_PREFIX_UPLOAD_TICKET + fileName.trim();
        String owner = redisTemplate.opsForValue().getAndDelete(key);
        if (!StringUtils.hasText(owner) || !owner.trim().equals(Integer.toString(userId))) {
            throw new BusinessException(FORBIDDEN, "上传凭证已失效或不匹配");
        }
    }

    public void assertUploadTicketOwner(int userId, String fileName) {
        if (userId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        if (!isSafeFileName(fileName)) {
            throw new BusinessException(INVALID_ARGUMENT, "fileName 非法");
        }
        if (redisTemplate == null) {
            throw new BusinessException(FORBIDDEN, "上传校验不可用");
        }
        String key = KEY_PREFIX_UPLOAD_TICKET + fileName.trim();
        String owner = redisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(owner) || !owner.trim().equals(Integer.toString(userId))) {
            throw new BusinessException(FORBIDDEN, "上传凭证已失效或不匹配");
        }
    }

    private void bindUploadTicket(int userId, String fileName) {
        if (redisTemplate == null) {
            throw new BusinessException(INVALID_ARGUMENT, "Redis 未配置，无法签发上传凭证");
        }
        String key = KEY_PREFIX_UPLOAD_TICKET + fileName;
        redisTemplate.opsForValue().set(key, Integer.toString(userId), UPLOAD_TICKET_TTL_SECONDS, TimeUnit.SECONDS);
    }

    public String buildAvatarUrl(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            throw new BusinessException(INVALID_ARGUMENT, "fileName 不能为空");
        }
        if (!isSafeFileName(fileName)) {
            throw new BusinessException(INVALID_ARGUMENT, "fileName 非法");
        }
        return currentProvider().buildAvatarUrl(fileName.trim());
    }

    private AvatarStorageProvider currentProvider() {
        if (storageRouter == null) {
            throw new BusinessException(INVALID_ARGUMENT, "头像存储路由未配置");
        }
        return storageRouter.currentProviderOrThrow();
    }

    private String generateFileName(int userId) {
        return AvatarConstraints.KEY_PREFIX + userId + "/" + UUID.randomUUID().toString().replace("-", "");
    }

    private boolean isSafeFileName(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return false;
        }
        String n = fileName.trim();
        if (!n.startsWith(AvatarConstraints.KEY_PREFIX)) {
            return false;
        }
        if (n.length() > 200) {
            return false;
        }
        // 路径穿越 / 兼容 Windows 分隔符
        if (n.contains("..") || n.contains("\\") || n.contains("\u0000")) {
            return false;
        }
        return true;
    }
}
