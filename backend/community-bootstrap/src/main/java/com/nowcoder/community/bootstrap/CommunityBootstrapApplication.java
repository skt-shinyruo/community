package com.nowcoder.community.bootstrap;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = "com.nowcoder.community")
@EnableScheduling
@MapperScan(
        annotationClass = org.apache.ibatis.annotations.Mapper.class,
        basePackages = "com.nowcoder.community"
)
@ComponentScan(
        basePackages = "com.nowcoder.community",
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = SpringBootApplication.class),
                @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = AutoConfiguration.class)
        }
)
public class CommunityBootstrapApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommunityBootstrapApplication.class, args);
    }
}
