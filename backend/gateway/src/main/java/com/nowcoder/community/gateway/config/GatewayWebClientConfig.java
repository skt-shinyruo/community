package com.nowcoder.community.gateway.config;

// Gateway WebClient 配置：为所有出站调用提供统一的连接池与超时兜底，避免极端网络条件下资源耗尽。
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableConfigurationProperties(GatewayWebClientProperties.class)
public class GatewayWebClientConfig {

    @Bean
    @LoadBalanced
    public WebClient.Builder loadBalancedWebClientBuilder(ReactorClientHttpConnector connector) {
        return WebClient.builder()
                .clientConnector(connector);
    }

    @Bean(destroyMethod = "dispose")
    public ConnectionProvider gatewayWebClientConnectionProvider(GatewayWebClientProperties properties) {
        GatewayWebClientProperties.Pool pool = properties == null ? null : properties.getPool();
        int maxConnections = Math.max(1, pool == null ? 500 : pool.getMaxConnections());

        ConnectionProvider.Builder builder = ConnectionProvider.builder("gateway-webclient")
                .maxConnections(maxConnections);

        if (pool != null) {
            int pendingAcquireTimeoutMs = pool.getPendingAcquireTimeoutMs();
            if (pendingAcquireTimeoutMs > 0) {
                builder.pendingAcquireTimeout(Duration.ofMillis(Math.max(50, pendingAcquireTimeoutMs)));
            }

            int pendingAcquireMaxCount = pool.getPendingAcquireMaxCount();
            builder.pendingAcquireMaxCount(Math.max(-1, pendingAcquireMaxCount));

            int maxIdleSeconds = pool.getMaxIdleTimeSeconds();
            if (maxIdleSeconds > 0) {
                builder.maxIdleTime(Duration.ofSeconds(maxIdleSeconds));
            }

            int maxLifeSeconds = pool.getMaxLifeTimeSeconds();
            if (maxLifeSeconds > 0) {
                builder.maxLifeTime(Duration.ofSeconds(maxLifeSeconds));
            }

            int evictSeconds = pool.getEvictInBackgroundSeconds();
            if (evictSeconds > 0) {
                builder.evictInBackground(Duration.ofSeconds(evictSeconds));
            }

            builder.metrics(pool.isMetricsEnabled());
        }

        return builder.build();
    }

    @Bean
    public HttpClient gatewayWebClientHttpClient(GatewayWebClientProperties properties, ConnectionProvider connectionProvider) {
        int connectTimeoutMs = properties == null ? 0 : properties.getConnectTimeoutMs();
        int responseTimeoutMs = properties == null ? 0 : properties.getResponseTimeoutMs();
        int readTimeoutMs = properties == null ? 0 : properties.getReadTimeoutMs();
        int writeTimeoutMs = properties == null ? 0 : properties.getWriteTimeoutMs();

        HttpClient client = HttpClient.create(connectionProvider);

        if (connectTimeoutMs > 0) {
            client = client.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.max(50, connectTimeoutMs));
        }
        if (responseTimeoutMs > 0) {
            client = client.responseTimeout(Duration.ofMillis(Math.max(50, responseTimeoutMs)));
        }

        if (readTimeoutMs > 0 || writeTimeoutMs > 0) {
            int rt = readTimeoutMs;
            int wt = writeTimeoutMs;
            client = client.doOnConnected(conn -> {
                if (rt > 0) {
                    conn.addHandlerLast(new ReadTimeoutHandler(rt, TimeUnit.MILLISECONDS));
                }
                if (wt > 0) {
                    conn.addHandlerLast(new WriteTimeoutHandler(wt, TimeUnit.MILLISECONDS));
                }
            });
        }

        return client;
    }

    @Bean
    public ReactorClientHttpConnector gatewayWebClientConnector(HttpClient httpClient) {
        return new ReactorClientHttpConnector(httpClient);
    }
}
