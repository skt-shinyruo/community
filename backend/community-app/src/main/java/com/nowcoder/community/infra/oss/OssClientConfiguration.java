package com.nowcoder.community.infra.oss;

import com.nowcoder.community.common.security.jwt.JwtProperties;
import com.nowcoder.community.oss.client.HttpCommunityOssClient;
import com.nowcoder.community.oss.client.OssServiceTokenProvider;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.observation.ClientRequestObservationConvention;
import org.springframework.web.client.RestClient;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OssClientProperties.class)
public class OssClientConfiguration {

    @Bean
    public OssServiceTokenProvider ossServiceTokenProvider(
            JwtProperties jwtProperties,
            OssClientProperties properties,
            Clock clock
    ) {
        return new JwtOssServiceTokenProvider(jwtProperties, properties, clock);
    }

    @Bean
    public HttpCommunityOssClient communityOssClient(
            OssClientProperties properties,
            ObjectProvider<RestClient.Builder> restClientBuilder,
            ObjectProvider<ClientHttpRequestFactory> requestFactory,
            ObjectProvider<ObservationRegistry> observationRegistry,
            ObjectProvider<ClientRequestObservationConvention> observationConvention,
            OssServiceTokenProvider serviceTokenProvider
    ) {
        RestClient.Builder multipartRestClientBuilder = RestClient.builder()
                .observationRegistry(observationRegistry.getIfAvailable(() -> ObservationRegistry.NOOP));
        ClientHttpRequestFactory multipartRequestFactory = requestFactory.getIfAvailable();
        if (multipartRequestFactory != null) {
            multipartRestClientBuilder.requestFactory(multipartRequestFactory);
        }
        ClientRequestObservationConvention multipartObservationConvention = observationConvention.getIfAvailable();
        if (multipartObservationConvention != null) {
            multipartRestClientBuilder.observationConvention(multipartObservationConvention);
        }
        return new HttpCommunityOssClient(
                properties.baseUrl(),
                restClientBuilder.getIfAvailable(RestClient::builder),
                multipartRestClientBuilder,
                serviceTokenProvider
        );
    }
}
