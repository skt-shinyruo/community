package com.nowcoder.community.infra.oss;

import com.nowcoder.community.common.observability.oss.OssRuntimeLogger;
import com.nowcoder.community.common.security.jwt.JwtProperties;
import com.nowcoder.community.infra.observability.ObservedCommunityOssClient;
import com.nowcoder.community.oss.client.CommunityOssClient;
import com.nowcoder.community.oss.client.HttpCommunityOssClient;
import com.nowcoder.community.oss.client.OssServiceTokenProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.web.client.RestClient;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OssClientProperties.class)
public class OssClientConfiguration {

    @Bean
    public OssServiceTokenProvider ossServiceTokenProvider(
            JwtEncoder jwtEncoder,
            JwtProperties jwtProperties,
            OssClientProperties properties,
            Clock clock
    ) {
        return new JwtOssServiceTokenProvider(jwtEncoder, jwtProperties, properties, clock);
    }

    @Bean
    public CommunityOssClient communityOssClient(
            OssClientProperties properties,
            ObjectProvider<RestClient.Builder> restClientBuilder,
            ObjectProvider<OssRuntimeLogger> ossRuntimeLogger,
            OssServiceTokenProvider serviceTokenProvider
    ) {
        CommunityOssClient client = new HttpCommunityOssClient(
                properties.baseUrl(),
                restClientBuilder.getIfAvailable(RestClient::builder),
                serviceTokenProvider
        );
        OssRuntimeLogger logger = ossRuntimeLogger.getIfAvailable();
        return logger == null ? client : new ObservedCommunityOssClient(client, logger);
    }
}
