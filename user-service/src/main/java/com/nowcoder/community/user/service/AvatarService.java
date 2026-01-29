package com.nowcoder.community.user.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.api.dto.AvatarUploadTokenResponse;
import com.nowcoder.community.user.config.QiniuProperties;
import com.qiniu.util.Auth;
import com.qiniu.util.StringMap;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;
import java.util.UUID;

import static com.nowcoder.community.common.api.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.api.CommonErrorCode.INVALID_ARGUMENT;

@Service
@EnableConfigurationProperties(QiniuProperties.class)
public class AvatarService {

    private static final String KEY_PREFIX_UPLOAD_TICKET = "user:avatar:upload:";
    private static final long UPLOAD_TICKET_TTL_SECONDS = 600;

    // 头像上传策略（服务侧兜底，优先在对象存储侧拒绝）
    private static final long MAX_AVATAR_BYTES = 2L * 1024 * 1024;
    private static final String MIME_LIMIT = "image/jpeg;image/png;image/webp;image/gif";
    private static final String KEY_PREFIX = "avatar/";

    private final QiniuProperties qiniuProperties;
    private final StringRedisTemplate redisTemplate;

    public AvatarService(QiniuProperties qiniuProperties, StringRedisTemplate redisTemplate) {
        this.qiniuProperties = qiniuProperties;
        this.redisTemplate = redisTemplate;
    }

    public AvatarUploadTokenResponse createUploadToken(int userId) {
        if (userId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        String accessKey = qiniuProperties.getKey().getAccess();
        String secretKey = qiniuProperties.getKey().getSecret();
        String bucketName = qiniuProperties.getBucket().getHeader().getName();
        String bucketUrl = qiniuProperties.getBucket().getHeader().getUrl();

        if (!StringUtils.hasText(accessKey) || !StringUtils.hasText(secretKey)) {
            throw new BusinessException(INVALID_ARGUMENT, "Qiniu access/secret 未配置");
        }
        if (!StringUtils.hasText(bucketName) || !StringUtils.hasText(bucketUrl)) {
            throw new BusinessException(INVALID_ARGUMENT, "Qiniu bucket 配置未完成");
        }

        String fileName = KEY_PREFIX + userId + "/" + UUID.randomUUID().toString().replace("-", "");
        StringMap policy = new StringMap();
        policy.put("returnBody", "{\"code\":0}");
        policy.put("fsizeLimit", MAX_AVATAR_BYTES);
        policy.put("mimeLimit", MIME_LIMIT);
        policy.put("insertOnly", 1);
        String uploadToken = Auth.create(accessKey, secretKey).uploadToken(bucketName, fileName, 3600, policy);

        bindUploadTicket(userId, fileName);

        AvatarUploadTokenResponse resp = new AvatarUploadTokenResponse();
        resp.setUploadToken(uploadToken);
        resp.setFileName(fileName);
        resp.setBucketUrl(bucketUrl);
        return resp;
    }

    public void assertAndConsumeUploadTicket(int userId, String fileName) {
        if (userId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        if (!StringUtils.hasText(fileName) || !fileName.startsWith(KEY_PREFIX)) {
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

    private void bindUploadTicket(int userId, String fileName) {
        if (redisTemplate == null) {
            throw new BusinessException(INVALID_ARGUMENT, "Redis 未配置，无法签发上传凭证");
        }
        String key = KEY_PREFIX_UPLOAD_TICKET + fileName;
        redisTemplate.opsForValue().set(key, Integer.toString(userId), UPLOAD_TICKET_TTL_SECONDS, TimeUnit.SECONDS);
    }

    public String buildAvatarUrl(String fileName) {
        String bucketUrl = qiniuProperties.getBucket().getHeader().getUrl();
        if (!StringUtils.hasText(bucketUrl)) {
            throw new BusinessException(INVALID_ARGUMENT, "Qiniu bucketUrl 未配置");
        }
        if (!StringUtils.hasText(fileName)) {
            throw new BusinessException(INVALID_ARGUMENT, "fileName 不能为空");
        }
        if (!fileName.startsWith(KEY_PREFIX)) {
            throw new BusinessException(INVALID_ARGUMENT, "fileName 非法");
        }
        return bucketUrl.endsWith("/") ? (bucketUrl + fileName) : (bucketUrl + "/" + fileName);
    }
}
