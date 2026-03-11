package com.nowcoder.community.user.service;

import com.nowcoder.community.contracts.exception.BusinessException;
import com.nowcoder.community.user.api.dto.AvatarUploadTokenResponse;
import com.nowcoder.community.user.config.QiniuProperties;
import com.qiniu.util.Auth;
import com.qiniu.util.StringMap;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import static com.nowcoder.community.contracts.api.CommonErrorCode.INVALID_ARGUMENT;

@Service
@EnableConfigurationProperties(QiniuProperties.class)
public class QiniuAvatarStorageProvider implements AvatarStorageProvider {

    private final QiniuProperties qiniuProperties;

    public QiniuAvatarStorageProvider(QiniuProperties qiniuProperties) {
        this.qiniuProperties = qiniuProperties;
    }

    @Override
    public String provider() {
        return "qiniu";
    }

    @Override
    public AvatarUploadTokenResponse createUploadToken(int userId, String fileName) {
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

        StringMap policy = new StringMap();
        policy.put("returnBody", "{\"code\":0}");
        policy.put("fsizeLimit", AvatarConstraints.MAX_AVATAR_BYTES);
        policy.put("mimeLimit", AvatarConstraints.MIME_LIMIT);
        policy.put("insertOnly", 1);
        String uploadToken = Auth.create(accessKey, secretKey).uploadToken(bucketName, fileName, 3600, policy);

        AvatarUploadTokenResponse resp = new AvatarUploadTokenResponse();
        resp.setProvider(provider());
        resp.setUploadToken(uploadToken);
        resp.setFileName(fileName);
        resp.setBucketUrl(bucketUrl);
        return resp;
    }

    @Override
    public void upload(int userId, String fileName, MultipartFile file) {
        throw new BusinessException(INVALID_ARGUMENT, "当前存储策略为 qiniu，请使用 uploadToken 进行客户端直传");
    }

    @Override
    public String buildAvatarUrl(String fileName) {
        String bucketUrl = qiniuProperties.getBucket().getHeader().getUrl();
        if (!StringUtils.hasText(bucketUrl)) {
            throw new BusinessException(INVALID_ARGUMENT, "Qiniu bucketUrl 未配置");
        }
        if (!StringUtils.hasText(fileName)) {
            throw new BusinessException(INVALID_ARGUMENT, "fileName 不能为空");
        }
        if (!fileName.startsWith(AvatarConstraints.KEY_PREFIX)) {
            throw new BusinessException(INVALID_ARGUMENT, "fileName 非法");
        }
        return bucketUrl.endsWith("/") ? (bucketUrl + fileName) : (bucketUrl + "/" + fileName);
    }

    @Override
    public StoredAvatar loadOrNull(String key) {
        return null;
    }
}
