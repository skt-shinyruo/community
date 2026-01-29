package com.nowcoder.community.common.autoconfig;

import com.nowcoder.community.common.startup.StartupValidationAutoConfig;
import com.nowcoder.community.common.net.TrustedProxyProperties;
import com.nowcoder.community.common.web.CommonJacksonConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/**
 * common 通用自动装配：
 * - 不依赖 servlet/reactive 细节（可在所有服务中生效，包括 gateway）
 * - 提供跨服务一致的基础能力（如 Jackson 时间序列化、启动期校验等）
 */
@AutoConfiguration
@EnableConfigurationProperties({
        TrustedProxyProperties.class
})
@Import({
        CommonJacksonConfig.class,
        StartupValidationAutoConfig.class
})
public class CommonAutoConfiguration {
}
