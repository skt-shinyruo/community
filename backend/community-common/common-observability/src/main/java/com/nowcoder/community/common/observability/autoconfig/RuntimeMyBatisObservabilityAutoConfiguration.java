package com.nowcoder.community.common.observability.autoconfig;

import com.nowcoder.community.common.observability.data.MyBatisSlowQueryInterceptor;
import com.nowcoder.community.common.observability.data.SqlRuntimeLogger;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(
        after = RuntimeObservabilityAutoConfiguration.class,
        beforeName = "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration"
)
@ConditionalOnClass(name = "org.apache.ibatis.plugin.Interceptor")
public class RuntimeMyBatisObservabilityAutoConfiguration {

    private static final String PREFIX = "community.observability.runtime-logging";

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(SqlRuntimeLogger.class)
    @ConditionalOnProperty(prefix = PREFIX + ".sql", name = "enabled", havingValue = "true", matchIfMissing = true)
    public MyBatisSlowQueryInterceptor myBatisSlowQueryInterceptor(SqlRuntimeLogger logger) {
        return new MyBatisSlowQueryInterceptor(logger);
    }
}
