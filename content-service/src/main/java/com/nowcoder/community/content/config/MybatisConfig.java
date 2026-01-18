package com.nowcoder.community.content.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.nowcoder.community.content.dao")
public class MybatisConfig {
}

