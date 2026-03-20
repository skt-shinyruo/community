package com.nowcoder.community.user.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

@Configuration
@EnableConfigurationProperties(R2Properties.class)
public class R2ClientConfig {

    @Bean
    @ConditionalOnProperty(name = "user.avatar.storage", havingValue = "r2")
    public S3Client r2S3Client(R2Properties properties) {
        if (properties == null) {
            throw new IllegalArgumentException("r2 properties missing");
        }

        String endpoint = trim(properties.getEndpoint());
        String accessKey = trim(properties.getAccessKey());
        String secretKey = trim(properties.getSecretKey());
        String region = StringUtils.hasText(properties.getRegion()) ? properties.getRegion().trim() : "auto";

        if (!StringUtils.hasText(endpoint)) {
            throw new IllegalArgumentException("r2.endpoint 未配置");
        }
        if (!StringUtils.hasText(accessKey) || !StringUtils.hasText(secretKey)) {
            throw new IllegalArgumentException("r2.access-key/r2.secret-key 未配置");
        }

        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.of(region))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.isPathStyle())
                        .build())
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }

    private String trim(String v) {
        return v == null ? "" : v.trim();
    }
}

