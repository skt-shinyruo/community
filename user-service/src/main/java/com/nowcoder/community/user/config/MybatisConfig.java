package com.nowcoder.community.user.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan({
        "com.nowcoder.community.user.dao",
        "com.nowcoder.community.user.outbox"
})
public class MybatisConfig {
}
