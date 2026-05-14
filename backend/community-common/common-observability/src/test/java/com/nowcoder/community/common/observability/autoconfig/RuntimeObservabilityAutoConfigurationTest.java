package com.nowcoder.community.common.observability.autoconfig;

import com.nowcoder.community.common.observability.data.DataSourceRuntimeLogger;
import com.nowcoder.community.common.observability.data.MyBatisSlowQueryInterceptor;
import com.nowcoder.community.common.observability.data.SqlRuntimeLogger;
import com.nowcoder.community.common.observability.executor.ExecutorRuntimeLogger;
import com.nowcoder.community.common.observability.http.ServletAccessRuntimeLogFilter;
import com.nowcoder.community.common.observability.http.HttpClientRuntimeLogger;
import com.nowcoder.community.common.observability.http.RuntimeRestClientCustomizer;
import com.nowcoder.community.common.observability.http.RuntimeWebClientCustomizer;
import com.nowcoder.community.common.observability.app.RuntimeApplicationLifecycleListener;
import com.nowcoder.community.common.observability.jvm.GcPauseThresholdLogger;
import com.nowcoder.community.common.observability.jvm.JvmExtendedRuntimeLogger;
import com.nowcoder.community.common.observability.jvm.JvmRuntimeLogger;
import com.nowcoder.community.common.observability.kafka.KafkaRuntimeLogger;
import com.nowcoder.community.common.observability.kafka.RuntimeKafkaProducerListener;
import com.nowcoder.community.common.observability.kafka.RuntimeKafkaRebalanceListener;
import com.nowcoder.community.common.observability.kafka.RuntimeKafkaRecordInterceptor;
import com.nowcoder.community.common.observability.redis.RedisRuntimeLogger;
import com.nowcoder.community.common.observability.oss.OssRuntimeLogger;
import com.nowcoder.community.common.observability.job.ScheduledJobRuntimeLogger;
import com.nowcoder.community.common.observability.cache.CacheRuntimeLogger;
import com.nowcoder.community.common.observability.security.SecurityRuntimeLogger;
import com.nowcoder.community.common.observability.logging.LoggingSystemRuntimeLogger;
import com.nowcoder.community.common.observability.logging.RuntimeLoggingProperties;
import com.nowcoder.community.common.observability.system.ProcessResourceRuntimeLogger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeObservabilityAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    RuntimeObservabilityAutoConfiguration.class,
                    RuntimeMyBatisObservabilityAutoConfiguration.class,
                    RuntimeKafkaObservabilityAutoConfiguration.class,
                    RuntimeWebClientObservabilityAutoConfiguration.class,
                    RuntimeServletObservabilityAutoConfiguration.class
            ));

    @Test
    void enablesRuntimeLoggingByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(RuntimeLoggingProperties.class);
            RuntimeLoggingProperties properties = context.getBean(RuntimeLoggingProperties.class);
            assertThat(properties.isEnabled()).isTrue();
            assertThat(properties.getJvm().isEnabled()).isTrue();
            assertThat(properties.getExecutors().isEnabled()).isTrue();
            assertThat(properties.getDatasource().isEnabled()).isTrue();
            assertThat(properties.getHttp().isAccessLogEnabled()).isTrue();

            assertThat(context).hasSingleBean(JvmRuntimeLogger.class);
            assertThat(context).hasSingleBean(RuntimeApplicationLifecycleListener.class);
            assertThat(context).hasSingleBean(GcPauseThresholdLogger.class);
            assertThat(context).hasSingleBean(JvmExtendedRuntimeLogger.class);
            assertThat(context).hasSingleBean(ExecutorRuntimeLogger.class);
            assertThat(context).hasSingleBean(DataSourceRuntimeLogger.class);
            assertThat(context).hasSingleBean(SqlRuntimeLogger.class);
            assertThat(context).hasSingleBean(MyBatisSlowQueryInterceptor.class);
            assertThat(context).hasSingleBean(RedisRuntimeLogger.class);
            assertThat(context).hasSingleBean(KafkaRuntimeLogger.class);
            assertThat(context).hasSingleBean(RuntimeKafkaProducerListener.class);
            assertThat(context).hasSingleBean(RuntimeKafkaRebalanceListener.class);
            assertThat(context).hasSingleBean(RuntimeKafkaRecordInterceptor.class);
            assertThat(context).hasSingleBean(OssRuntimeLogger.class);
            assertThat(context).hasSingleBean(HttpClientRuntimeLogger.class);
            assertThat(context).hasSingleBean(RuntimeRestClientCustomizer.class);
            assertThat(context).hasSingleBean(RuntimeWebClientCustomizer.class);
            assertThat(context).hasSingleBean(ScheduledJobRuntimeLogger.class);
            assertThat(context).hasSingleBean(CacheRuntimeLogger.class);
            assertThat(context).hasSingleBean(SecurityRuntimeLogger.class);
            assertThat(context).hasSingleBean(LoggingSystemRuntimeLogger.class);
            assertThat(context).hasSingleBean(ProcessResourceRuntimeLogger.class);
            assertThat(context).hasSingleBean(ServletAccessRuntimeLogFilter.class);
            assertThat(context).hasBean("runtimeServletAccessLogFilterRegistration");
        });
    }

    @Test
    void disablesAllRuntimeLoggingBeansWhenMasterSwitchIsOff() {
        contextRunner
                .withPropertyValues("community.observability.runtime-logging.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(RuntimeLoggingProperties.class);
                    assertThat(context).doesNotHaveBean(JvmRuntimeLogger.class);
                    assertThat(context).doesNotHaveBean(RuntimeApplicationLifecycleListener.class);
                    assertThat(context).doesNotHaveBean(GcPauseThresholdLogger.class);
                    assertThat(context).doesNotHaveBean(JvmExtendedRuntimeLogger.class);
                    assertThat(context).doesNotHaveBean(ExecutorRuntimeLogger.class);
                    assertThat(context).doesNotHaveBean(DataSourceRuntimeLogger.class);
                    assertThat(context).doesNotHaveBean(SqlRuntimeLogger.class);
                    assertThat(context).doesNotHaveBean(MyBatisSlowQueryInterceptor.class);
                    assertThat(context).doesNotHaveBean(RedisRuntimeLogger.class);
                    assertThat(context).doesNotHaveBean(KafkaRuntimeLogger.class);
                    assertThat(context).doesNotHaveBean(RuntimeKafkaProducerListener.class);
                    assertThat(context).doesNotHaveBean(RuntimeKafkaRebalanceListener.class);
                    assertThat(context).doesNotHaveBean(RuntimeKafkaRecordInterceptor.class);
                    assertThat(context).doesNotHaveBean(OssRuntimeLogger.class);
                    assertThat(context).doesNotHaveBean(HttpClientRuntimeLogger.class);
                    assertThat(context).doesNotHaveBean(RuntimeRestClientCustomizer.class);
                    assertThat(context).doesNotHaveBean(RuntimeWebClientCustomizer.class);
                    assertThat(context).doesNotHaveBean(ScheduledJobRuntimeLogger.class);
                    assertThat(context).doesNotHaveBean(CacheRuntimeLogger.class);
                    assertThat(context).doesNotHaveBean(SecurityRuntimeLogger.class);
                    assertThat(context).doesNotHaveBean(LoggingSystemRuntimeLogger.class);
                    assertThat(context).doesNotHaveBean(ProcessResourceRuntimeLogger.class);
                    assertThat(context).doesNotHaveBean(ServletAccessRuntimeLogFilter.class);
                    assertThat(context).doesNotHaveBean(FilterRegistrationBean.class);
                });
    }

    @Test
    void disablesSubsystemBeansIndependently() {
        contextRunner
                .withPropertyValues(
                        "community.observability.runtime-logging.jvm.enabled=false",
                        "community.observability.runtime-logging.executors.enabled=false",
                        "community.observability.runtime-logging.datasource.enabled=false",
                        "community.observability.runtime-logging.sql.enabled=false",
                        "community.observability.runtime-logging.redis.enabled=false",
                        "community.observability.runtime-logging.kafka.enabled=false",
                        "community.observability.runtime-logging.oss.enabled=false",
                        "community.observability.runtime-logging.http-client.enabled=false",
                        "community.observability.runtime-logging.jobs.enabled=false",
                        "community.observability.runtime-logging.cache.enabled=false",
                        "community.observability.runtime-logging.security.enabled=false",
                        "community.observability.runtime-logging.logging-system.enabled=false",
                        "community.observability.runtime-logging.system.enabled=false",
                        "community.observability.runtime-logging.http.access-log-enabled=false"
                )
                .run(context -> {
                    assertThat(context).doesNotHaveBean(JvmRuntimeLogger.class);
                    assertThat(context).doesNotHaveBean(GcPauseThresholdLogger.class);
                    assertThat(context).doesNotHaveBean(JvmExtendedRuntimeLogger.class);
                    assertThat(context).doesNotHaveBean(ExecutorRuntimeLogger.class);
                    assertThat(context).doesNotHaveBean(DataSourceRuntimeLogger.class);
                    assertThat(context).doesNotHaveBean(SqlRuntimeLogger.class);
                    assertThat(context).doesNotHaveBean(MyBatisSlowQueryInterceptor.class);
                    assertThat(context).doesNotHaveBean(RedisRuntimeLogger.class);
                    assertThat(context).doesNotHaveBean(KafkaRuntimeLogger.class);
                    assertThat(context).doesNotHaveBean(RuntimeKafkaProducerListener.class);
                    assertThat(context).doesNotHaveBean(RuntimeKafkaRebalanceListener.class);
                    assertThat(context).doesNotHaveBean(RuntimeKafkaRecordInterceptor.class);
                    assertThat(context).doesNotHaveBean(OssRuntimeLogger.class);
                    assertThat(context).doesNotHaveBean(HttpClientRuntimeLogger.class);
                    assertThat(context).doesNotHaveBean(RuntimeRestClientCustomizer.class);
                    assertThat(context).doesNotHaveBean(RuntimeWebClientCustomizer.class);
                    assertThat(context).doesNotHaveBean(ScheduledJobRuntimeLogger.class);
                    assertThat(context).doesNotHaveBean(CacheRuntimeLogger.class);
                    assertThat(context).doesNotHaveBean(SecurityRuntimeLogger.class);
                    assertThat(context).doesNotHaveBean(LoggingSystemRuntimeLogger.class);
                    assertThat(context).doesNotHaveBean(ProcessResourceRuntimeLogger.class);
                    assertThat(context).doesNotHaveBean(ServletAccessRuntimeLogFilter.class);
                });
    }

    @Test
    void skipsOptionalInstrumentationWhenOptionalStacksAreAbsent() {
        contextRunner
                .withClassLoader(new FilteredClassLoader(
                        "jakarta.servlet",
                        "org.apache.ibatis",
                        "org.springframework.kafka",
                        "org.springframework.web.reactive"
                ))
                .run(context -> {
                    assertThat(context).hasSingleBean(RuntimeLoggingProperties.class);
                    assertThat(context).hasSingleBean(HttpClientRuntimeLogger.class);
                    assertThat(context).doesNotHaveBean("myBatisSlowQueryInterceptor");
                    assertThat(context).doesNotHaveBean("runtimeKafkaProducerListener");
                    assertThat(context).doesNotHaveBean("runtimeKafkaRebalanceListener");
                    assertThat(context).doesNotHaveBean("runtimeKafkaRecordInterceptor");
                    assertThat(context).doesNotHaveBean("runtimeWebClientCustomizer");
                    assertThat(context).doesNotHaveBean("servletAccessRuntimeLogFilter");
                    assertThat(context).doesNotHaveBean("runtimeServletAccessLogFilterRegistration");
                });
    }

    @Test
    void keepsPeriodicJvmSnapshotsWhenExecutorAndDatasourceLoggersAreDisabled() {
        contextRunner
                .withPropertyValues(
                        "community.observability.runtime-logging.executors.enabled=false",
                        "community.observability.runtime-logging.datasource.enabled=false"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(JvmRuntimeLogger.class);
                    assertThat(context).doesNotHaveBean(ExecutorRuntimeLogger.class);
                    assertThat(context).doesNotHaveBean(DataSourceRuntimeLogger.class);
                    assertThat(context).hasSingleBean(RuntimeSnapshotScheduler.class);
                });
    }

    @Test
    void periodicSnapshotSchedulerCanBeProcessedBySpringScheduling() {
        contextRunner
                .withUserConfiguration(SchedulingEnabledConfig.class)
                .withPropertyValues("community.observability.runtime-logging.periodic-summary-interval=1h")
                .run(context -> assertThat(context).hasSingleBean(RuntimeSnapshotScheduler.class));
    }

    @Test
    void bindsThresholdProperties() {
        contextRunner
                .withPropertyValues(
                        "community.observability.runtime-logging.periodic-summary-interval=30s",
                        "community.observability.runtime-logging.jvm.memory-threshold-percent=91",
                        "community.observability.runtime-logging.jvm.gc-pause-threshold-ms=321",
                        "community.observability.runtime-logging.jvm.direct-memory-threshold-percent=92",
                        "community.observability.runtime-logging.executors.saturation-threshold-percent=77",
                        "community.observability.runtime-logging.datasource.pool-pending-threshold=3",
                        "community.observability.runtime-logging.sql.slow-query-threshold-ms=654",
                        "community.observability.runtime-logging.redis.pool-pending-threshold=4",
                        "community.observability.runtime-logging.redis.slow-command-threshold-ms=44",
                        "community.observability.runtime-logging.kafka.consumer-lag-threshold=123",
                        "community.observability.runtime-logging.oss.slow-operation-threshold-ms=222",
                        "community.observability.runtime-logging.http-client.slow-request-threshold-ms=333",
                        "community.observability.runtime-logging.jobs.slow-job-threshold-ms=4444",
                        "community.observability.runtime-logging.cache.hit-ratio-threshold-percent=70",
                        "community.observability.runtime-logging.logging-system.queue-pressure-threshold-percent=66",
                        "community.observability.runtime-logging.system.fd-usage-threshold-percent=60",
                        "community.observability.runtime-logging.system.disk-usage-threshold-percent=61",
                        "community.observability.runtime-logging.system.cpu-load-threshold-percent=62",
                        "community.observability.runtime-logging.http.slow-request-threshold-ms=456",
                        "community.observability.runtime-logging.http.exclude-paths=/actuator/health,/internal/noise"
                )
                .run(context -> {
                    RuntimeLoggingProperties properties = context.getBean(RuntimeLoggingProperties.class);
                    assertThat(properties.getPeriodicSummaryInterval()).hasSeconds(30);
                    assertThat(properties.getJvm().getMemoryThresholdPercent()).isEqualTo(91);
                    assertThat(properties.getJvm().getGcPauseThresholdMs()).isEqualTo(321);
                    assertThat(properties.getJvm().getDirectMemoryThresholdPercent()).isEqualTo(92);
                    assertThat(properties.getExecutors().getSaturationThresholdPercent()).isEqualTo(77);
                    assertThat(properties.getDatasource().getPoolPendingThreshold()).isEqualTo(3);
                    assertThat(properties.getSql().getSlowQueryThresholdMs()).isEqualTo(654);
                    assertThat(properties.getRedis().getPoolPendingThreshold()).isEqualTo(4);
                    assertThat(properties.getRedis().getSlowCommandThresholdMs()).isEqualTo(44);
                    assertThat(properties.getKafka().getConsumerLagThreshold()).isEqualTo(123);
                    assertThat(properties.getOss().getSlowOperationThresholdMs()).isEqualTo(222);
                    assertThat(properties.getHttpClient().getSlowRequestThresholdMs()).isEqualTo(333);
                    assertThat(properties.getJobs().getSlowJobThresholdMs()).isEqualTo(4444);
                    assertThat(properties.getCache().getHitRatioThresholdPercent()).isEqualTo(70);
                    assertThat(properties.getLoggingSystem().getQueuePressureThresholdPercent()).isEqualTo(66);
                    assertThat(properties.getSystem().getFdUsageThresholdPercent()).isEqualTo(60);
                    assertThat(properties.getSystem().getDiskUsageThresholdPercent()).isEqualTo(61);
                    assertThat(properties.getSystem().getCpuLoadThresholdPercent()).isEqualTo(62);
                    assertThat(properties.getHttp().getSlowRequestThresholdMs()).isEqualTo(456);
                    assertThat(properties.getHttp().getExcludePaths()).containsExactly("/actuator/health", "/internal/noise");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    static class SchedulingEnabledConfig {
    }
}
