package com.nowcoder.community.oss.infrastructure.config;

import com.nowcoder.community.oss.infrastructure.storage.LocalFilesystemObjectStore;
import com.nowcoder.community.oss.infrastructure.storage.ObjectStore;
import com.nowcoder.community.oss.infrastructure.storage.S3CompatibleObjectStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;

@Configuration
public class OssInfrastructureConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public Clock ossClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    public ObjectStore objectStore(OssProperties properties) {
        OssProperties.ObjectStoreProperties store = properties.objectStore();
        String mode = store.mode() == null ? "garage" : store.mode().trim().toLowerCase();
        if ("local".equals(mode) || "filesystem".equals(mode) || "local-filesystem".equals(mode)) {
            return new LocalFilesystemObjectStore(Path.of(store.localRoot()), properties.publicBaseUrl());
        }
        return new S3CompatibleObjectStore(s3Client(store), s3Presigner(store));
    }

    private S3Client s3Client(OssProperties.ObjectStoreProperties store) {
        return S3Client.builder()
                .region(Region.US_EAST_1)
                .endpointOverride(URI.create(store.endpoint()))
                .credentialsProvider(credentials(store))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(store.pathStyle())
                        .build())
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }

    private S3Presigner s3Presigner(OssProperties.ObjectStoreProperties store) {
        return S3Presigner.builder()
                .region(Region.US_EAST_1)
                .endpointOverride(URI.create(store.endpoint()))
                .credentialsProvider(credentials(store))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(store.pathStyle())
                        .build())
                .build();
    }

    private StaticCredentialsProvider credentials(OssProperties.ObjectStoreProperties store) {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(
                blankToPlaceholder(store.accessKey()),
                blankToPlaceholder(store.secretKey())
        ));
    }

    private String blankToPlaceholder(String value) {
        return value == null || value.isBlank() ? "change-me" : value.trim();
    }
}
