package com.nowcoder.community.infra.web.autoconfig;

import com.nowcoder.community.infra.web.CommonJacksonConfig;
import com.nowcoder.community.infra.web.net.TrustedProxyProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@EnableConfigurationProperties(TrustedProxyProperties.class)
@Import(CommonJacksonConfig.class)
public class WebInfraAutoConfiguration {
}

