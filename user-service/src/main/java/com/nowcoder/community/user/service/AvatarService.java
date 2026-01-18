package com.nowcoder.community.user.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.api.dto.AvatarUploadTokenResponse;
import com.nowcoder.community.user.config.QiniuProperties;
import com.qiniu.util.Auth;
import com.qiniu.util.StringMap;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.UUID;

import static com.nowcoder.community.common.api.CommonErrorCode.INVALID_ARGUMENT;

@Service
@EnableConfigurationProperties(QiniuProperties.class)
public class AvatarService {

    private final QiniuProperties qiniuProperties;

    public AvatarService(QiniuProperties qiniuProperties) {
        this.qiniuProperties = qiniuProperties;
    }

    public AvatarUploadTokenResponse createUploadToken() {
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

        String fileName = UUID.randomUUID().toString().replace("-", "");
        StringMap policy = new StringMap();
        policy.put("returnBody", "{\"code\":0}");
        String uploadToken = Auth.create(accessKey, secretKey).uploadToken(bucketName, fileName, 3600, policy);

        AvatarUploadTokenResponse resp = new AvatarUploadTokenResponse();
        resp.setUploadToken(uploadToken);
        resp.setFileName(fileName);
        resp.setBucketUrl(bucketUrl);
        return resp;
    }

    public String buildAvatarUrl(String fileName) {
        String bucketUrl = qiniuProperties.getBucket().getHeader().getUrl();
        if (!StringUtils.hasText(bucketUrl)) {
            throw new BusinessException(INVALID_ARGUMENT, "Qiniu bucketUrl 未配置");
        }
        if (!StringUtils.hasText(fileName)) {
            throw new BusinessException(INVALID_ARGUMENT, "fileName 不能为空");
        }
        return bucketUrl.endsWith("/") ? (bucketUrl + fileName) : (bucketUrl + "/" + fileName);
    }
}

