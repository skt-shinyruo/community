package com.nowcoder.community.common.observability.autoconfig;

import com.nowcoder.community.common.observability.app.ApplicationLifecycleRuntimeLogger;
import com.nowcoder.community.common.observability.app.RuntimeApplicationLifecycleListener;
import com.nowcoder.community.common.observability.cache.CacheRuntimeLogger;
import com.nowcoder.community.common.observability.data.DataSourceRuntimeLogger;
import com.nowcoder.community.common.observability.data.SqlRuntimeLogger;
import com.nowcoder.community.common.observability.executor.ExecutorRuntimeLogger;
import com.nowcoder.community.common.observability.http.HttpClientRuntimeLogger;
import com.nowcoder.community.common.observability.http.RuntimeRestClientCustomizer;
import com.nowcoder.community.common.observability.jvm.GcPauseThresholdLogger;
import com.nowcoder.community.common.observability.jvm.JvmExtendedRuntimeLogger;
import com.nowcoder.community.common.observability.jvm.JvmRuntimeLogger;
import com.nowcoder.community.common.observability.kafka.KafkaRuntimeLogger;
import com.nowcoder.community.common.observability.job.ScheduledJobRuntimeLogger;
import com.nowcoder.community.common.observability.logging.LoggingSystemRuntimeLogger;
import com.nowcoder.community.common.observability.logging.RuntimeLogWriter;
import com.nowcoder.community.common.observability.logging.RuntimeLoggingProperties;
import com.nowcoder.community.common.observability.oss.OssRuntimeLogger;
import com.nowcoder.community.common.observability.redis.RedisRuntimeLogger;
import com.nowcoder.community.common.observability.security.SecurityRuntimeLogger;
import com.nowcoder.community.common.observability.system.ProcessResourceRuntimeLogger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.Executor;

@AutoConfiguration(beforeName = {
        "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration",
        "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
        "org.springframework.boot.autoconfigure.kafka.KafkaAnnotationDrivenConfiguration"
})
@EnableConfigurationProperties(RuntimeLoggingProperties.class)
public class RuntimeObservabilityAutoConfiguration {

    private static final String PREFIX = "community.observability.runtime-logging";

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
    public RuntimeLogWriter runtimeLogWriter() {
        return new RuntimeLogWriter(LoggerFactory.getLogger("com.nowcoder.community.runtime"));
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(RuntimeLogWriter.class)
    public ApplicationLifecycleRuntimeLogger applicationLifecycleRuntimeLogger(
            RuntimeLogWriter logWriter,
            RuntimeLoggingProperties properties,
            Environment environment
    ) {
        String serviceName = environment.getProperty("spring.application.name", "community-service");
        String serviceVersion = environment.getProperty("community.logging.service-version", "unknown");
        int serverPort = environment.getProperty("server.port", Integer.class, -1);
        return new ApplicationLifecycleRuntimeLogger(
                logWriter,
                properties,
                serviceName,
                serviceVersion,
                Arrays.asList(environment.getActiveProfiles()),
                serverPort
        );
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(ApplicationLifecycleRuntimeLogger.class)
    public RuntimeApplicationLifecycleListener runtimeApplicationLifecycleListener(
            ApplicationLifecycleRuntimeLogger logger,
            Environment environment
    ) {
        Duration timeout = environment.getProperty("spring.lifecycle.timeout-per-shutdown-phase", Duration.class, Duration.ZERO);
        return new RuntimeApplicationLifecycleListener(logger, timeout);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(RuntimeLogWriter.class)
    @ConditionalOnProperty(prefix = PREFIX + ".jvm", name = "enabled", havingValue = "true", matchIfMissing = true)
    public JvmRuntimeLogger jvmRuntimeLogger(RuntimeLogWriter logWriter, RuntimeLoggingProperties properties) {
        return new JvmRuntimeLogger(logWriter, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(RuntimeLogWriter.class)
    @ConditionalOnProperty(prefix = PREFIX + ".jvm", name = "enabled", havingValue = "true", matchIfMissing = true)
    public GcPauseThresholdLogger gcPauseThresholdLogger(RuntimeLogWriter logWriter, RuntimeLoggingProperties properties) {
        return new GcPauseThresholdLogger(logWriter, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(RuntimeLogWriter.class)
    @ConditionalOnProperty(prefix = PREFIX + ".jvm", name = "enabled", havingValue = "true", matchIfMissing = true)
    public JvmExtendedRuntimeLogger jvmExtendedRuntimeLogger(RuntimeLogWriter logWriter, RuntimeLoggingProperties properties) {
        return new JvmExtendedRuntimeLogger(logWriter, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(RuntimeLogWriter.class)
    @ConditionalOnProperty(prefix = PREFIX + ".executors", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ExecutorRuntimeLogger executorRuntimeLogger(
            RuntimeLogWriter logWriter,
            RuntimeLoggingProperties properties,
            ObjectProvider<Map<String, Executor>> executors
    ) {
        Map<String, Executor> availableExecutors = executors.getIfAvailable(Map::of);
        return new ExecutorRuntimeLogger(logWriter, properties, availableExecutors);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(RuntimeLogWriter.class)
    @ConditionalOnProperty(prefix = PREFIX + ".datasource", name = "enabled", havingValue = "true", matchIfMissing = true)
    public DataSourceRuntimeLogger dataSourceRuntimeLogger(
            RuntimeLogWriter logWriter,
            RuntimeLoggingProperties properties,
            ObjectProvider<DataSource> dataSource
    ) {
        return new DataSourceRuntimeLogger(logWriter, properties, dataSource.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(RuntimeLogWriter.class)
    @ConditionalOnProperty(prefix = PREFIX + ".sql", name = "enabled", havingValue = "true", matchIfMissing = true)
    public SqlRuntimeLogger sqlRuntimeLogger(RuntimeLogWriter logWriter, RuntimeLoggingProperties properties) {
        return new SqlRuntimeLogger(logWriter, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(RuntimeLogWriter.class)
    @ConditionalOnProperty(prefix = PREFIX + ".redis", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RedisRuntimeLogger redisRuntimeLogger(RuntimeLogWriter logWriter, RuntimeLoggingProperties properties) {
        return new RedisRuntimeLogger(logWriter, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(RuntimeLogWriter.class)
    @ConditionalOnProperty(prefix = PREFIX + ".kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
    public KafkaRuntimeLogger kafkaRuntimeLogger(RuntimeLogWriter logWriter, RuntimeLoggingProperties properties) {
        return new KafkaRuntimeLogger(logWriter, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(RuntimeLogWriter.class)
    @ConditionalOnProperty(prefix = PREFIX + ".oss", name = "enabled", havingValue = "true", matchIfMissing = true)
    public OssRuntimeLogger ossRuntimeLogger(RuntimeLogWriter logWriter, RuntimeLoggingProperties properties) {
        return new OssRuntimeLogger(logWriter, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(RuntimeLogWriter.class)
    @ConditionalOnProperty(prefix = PREFIX + ".http-client", name = "enabled", havingValue = "true", matchIfMissing = true)
    public HttpClientRuntimeLogger httpClientRuntimeLogger(RuntimeLogWriter logWriter, RuntimeLoggingProperties properties) {
        return new HttpClientRuntimeLogger(logWriter, properties);
    }

    @Bean
    @ConditionalOnMissingBean(RuntimeRestClientCustomizer.class)
    @ConditionalOnClass(name = "org.springframework.web.client.RestClient")
    @ConditionalOnBean(HttpClientRuntimeLogger.class)
    @ConditionalOnProperty(prefix = PREFIX + ".http-client", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RestClientCustomizer runtimeRestClientCustomizer(HttpClientRuntimeLogger logger) {
        return new RuntimeRestClientCustomizer(logger);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(RuntimeLogWriter.class)
    @ConditionalOnProperty(prefix = PREFIX + ".jobs", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ScheduledJobRuntimeLogger scheduledJobRuntimeLogger(RuntimeLogWriter logWriter, RuntimeLoggingProperties properties) {
        return new ScheduledJobRuntimeLogger(logWriter, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(RuntimeLogWriter.class)
    @ConditionalOnProperty(prefix = PREFIX + ".cache", name = "enabled", havingValue = "true", matchIfMissing = true)
    public CacheRuntimeLogger cacheRuntimeLogger(RuntimeLogWriter logWriter, RuntimeLoggingProperties properties) {
        return new CacheRuntimeLogger(logWriter, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(RuntimeLogWriter.class)
    @ConditionalOnProperty(prefix = PREFIX + ".security", name = "enabled", havingValue = "true", matchIfMissing = true)
    public SecurityRuntimeLogger securityRuntimeLogger(RuntimeLogWriter logWriter, RuntimeLoggingProperties properties) {
        return new SecurityRuntimeLogger(logWriter, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(RuntimeLogWriter.class)
    @ConditionalOnProperty(prefix = PREFIX + ".logging-system", name = "enabled", havingValue = "true", matchIfMissing = true)
    public LoggingSystemRuntimeLogger loggingSystemRuntimeLogger(RuntimeLogWriter logWriter, RuntimeLoggingProperties properties) {
        return new LoggingSystemRuntimeLogger(logWriter, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(RuntimeLogWriter.class)
    @ConditionalOnProperty(prefix = PREFIX + ".system", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ProcessResourceRuntimeLogger processResourceRuntimeLogger(RuntimeLogWriter logWriter, RuntimeLoggingProperties properties) {
        return new ProcessResourceRuntimeLogger(logWriter, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(JvmRuntimeLogger.class)
    @ConditionalOnProperty(prefix = PREFIX, name = "startup-summary-enabled", havingValue = "true", matchIfMissing = true)
    public RuntimeStartupLogger runtimeStartupLogger(JvmRuntimeLogger jvmRuntimeLogger) {
        return new RuntimeStartupLogger(jvmRuntimeLogger);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(JvmRuntimeLogger.class)
    @ConditionalOnProperty(prefix = PREFIX, name = "periodic-summary-enabled", havingValue = "true", matchIfMissing = true)
    public RuntimeSnapshotScheduler runtimeSnapshotScheduler(
            JvmRuntimeLogger jvmRuntimeLogger,
            ObjectProvider<JvmExtendedRuntimeLogger> jvmExtendedRuntimeLogger,
            ObjectProvider<ExecutorRuntimeLogger> executorRuntimeLogger,
            ObjectProvider<DataSourceRuntimeLogger> dataSourceRuntimeLogger,
            ObjectProvider<ProcessResourceRuntimeLogger> processResourceRuntimeLogger,
            RuntimeLoggingProperties properties
    ) {
        return new RuntimeSnapshotScheduler(
                jvmRuntimeLogger,
                jvmExtendedRuntimeLogger,
                executorRuntimeLogger,
                dataSourceRuntimeLogger,
                processResourceRuntimeLogger,
                properties
        );
    }
}
