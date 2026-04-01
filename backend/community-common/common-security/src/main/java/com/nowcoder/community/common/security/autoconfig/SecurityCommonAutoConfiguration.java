package com.nowcoder.community.common.security.autoconfig;

import com.nowcoder.community.common.security.jwt.JwtProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@AutoConfiguration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityCommonAutoConfiguration {
}
