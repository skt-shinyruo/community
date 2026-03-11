package com.nowcoder.community.user.service;

import com.nowcoder.community.contracts.exception.BusinessException;
import com.nowcoder.community.user.config.AvatarStorageProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.nowcoder.community.contracts.api.CommonErrorCode.INVALID_ARGUMENT;

@Component
@EnableConfigurationProperties(AvatarStorageProperties.class)
public class AvatarStorageRouter {

    private final AvatarStorageProperties properties;
    private final Map<String, AvatarStorageProvider> providers;

    public AvatarStorageRouter(AvatarStorageProperties properties, List<AvatarStorageProvider> providers) {
        this.properties = properties;
        this.providers = buildProviderMap(providers);
    }

    public AvatarStorageProvider currentProviderOrThrow() {
        String configured = properties == null ? "" : properties.getStorage();
        String key = StringUtils.hasText(configured) ? configured.trim().toLowerCase() : "local";
        AvatarStorageProvider provider = providers.get(key);
        if (provider == null) {
            throw new BusinessException(INVALID_ARGUMENT, "未知头像存储策略：" + key);
        }
        return provider;
    }

    private Map<String, AvatarStorageProvider> buildProviderMap(List<AvatarStorageProvider> list) {
        Map<String, AvatarStorageProvider> map = new HashMap<>();
        if (list == null) {
            return map;
        }
        for (AvatarStorageProvider p : list) {
            if (p == null || !StringUtils.hasText(p.provider())) {
                continue;
            }
            map.put(p.provider().trim().toLowerCase(), p);
        }
        return map;
    }
}

