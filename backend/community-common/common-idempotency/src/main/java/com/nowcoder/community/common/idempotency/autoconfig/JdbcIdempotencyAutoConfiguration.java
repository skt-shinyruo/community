package com.nowcoder.community.common.idempotency.autoconfig;

import com.nowcoder.community.common.idempotency.IdempotencyStore;
import com.nowcoder.community.common.idempotency.JdbcIdempotencyStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

@AutoConfiguration(after = JdbcTemplateAutoConfiguration.class)
@ConditionalOnProperty(prefix = "http.idempotency", name = "enabled", havingValue = "true")
@ConditionalOnClass(JdbcTemplate.class)
public class JdbcIdempotencyAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "http.idempotency", name = "store", havingValue = "DB")
    @ConditionalOnMissingBean
    public IdempotencyStore idempotencyStore(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider == null ? null : jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            throw new IllegalStateException("http.idempotency.store=DB 需要 JdbcTemplate");
        }
        return new JdbcIdempotencyStore(jdbcTemplate);
    }
}
