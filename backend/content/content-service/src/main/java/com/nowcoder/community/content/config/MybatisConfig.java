package com.nowcoder.community.content.config;

// MyBatis 扫描配置：覆盖 dao 与 outbox mapper。
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan({
        "com.nowcoder.community.content.dao",
        "com.nowcoder.community.infra.outbox"
})
public class MybatisConfig {
}
