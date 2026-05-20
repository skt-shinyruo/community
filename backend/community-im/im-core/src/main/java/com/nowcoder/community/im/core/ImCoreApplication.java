package com.nowcoder.community.im.core;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan(
        annotationClass = Mapper.class,
        basePackages = "com.nowcoder.community.im.core.infrastructure.persistence.mapper"
)
public class ImCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(ImCoreApplication.class, args);
    }
}
