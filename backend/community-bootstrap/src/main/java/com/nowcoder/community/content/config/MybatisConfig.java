package com.nowcoder.community.content.config;

// MyBatis 扫描配置：覆盖 content 相关 mapper。
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.nowcoder.community.content.dao")
public class MybatisConfig {
}
