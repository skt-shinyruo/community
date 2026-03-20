package com.nowcoder.community.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 用户主页社交聚合配置。
 */
@Component
@ConfigurationProperties(prefix = "user.social-profile")
public class UserSocialProfileProperties {

    /**
     * 允许在社交聚合失败时返回降级结果，仅建议用于非关键展示字段。
     */
    private boolean degradeOnError = true;

    public boolean isDegradeOnError() {
        return degradeOnError;
    }

    public void setDegradeOnError(boolean degradeOnError) {
        this.degradeOnError = degradeOnError;
    }
}
