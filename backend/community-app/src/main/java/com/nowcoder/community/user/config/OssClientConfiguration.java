package com.nowcoder.community.user.config;

import com.nowcoder.community.oss.client.CommunityOssClient;
import com.nowcoder.community.oss.client.HttpCommunityOssClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OssClientConfiguration {

    @Bean
    public CommunityOssClient communityOssClient(@Value("${oss.client.base-url:http://community-oss:18090}") String baseUrl) {
        return new HttpCommunityOssClient(baseUrl);
    }
}
