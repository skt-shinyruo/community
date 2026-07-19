package com.nowcoder.community.user.config;

import com.nowcoder.community.common.observability.oss.OssRuntimeLogger;
import com.nowcoder.community.infra.observability.ObservedCommunityOssClient;
import com.nowcoder.community.oss.client.CommunityOssClient;
import com.nowcoder.community.oss.client.HttpCommunityOssClient;
import com.nowcoder.community.oss.client.OssServiceTokenProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class OssClientConfiguration {

    @Bean
    public CommunityOssClient communityOssClient(
            @Value("${oss.client.base-url:http://community-oss:18090}") String baseUrl,
            ObjectProvider<RestClient.Builder> restClientBuilder,
            ObjectProvider<OssRuntimeLogger> ossRuntimeLogger,
            OssServiceTokenProvider serviceTokenProvider
    ) {
        CommunityOssClient client = new HttpCommunityOssClient(
                baseUrl,
                restClientBuilder.getIfAvailable(RestClient::builder),
                serviceTokenProvider
        );
        OssRuntimeLogger logger = ossRuntimeLogger.getIfAvailable();
        return logger == null ? client : new ObservedCommunityOssClient(client, logger);
    }
}
