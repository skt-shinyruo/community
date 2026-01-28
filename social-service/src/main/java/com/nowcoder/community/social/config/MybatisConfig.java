package com.nowcoder.community.social.config;

// MyBatis 扫描配置：覆盖 social 关系表与 outbox 的 mapper。
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan(
        annotationClass = org.apache.ibatis.annotations.Mapper.class,
        basePackages = {
                "com.nowcoder.community.social.like",
                "com.nowcoder.community.social.follow",
                "com.nowcoder.community.social.block",
                "com.nowcoder.community.social.outbox"
        }
)
public class MybatisConfig {
}
