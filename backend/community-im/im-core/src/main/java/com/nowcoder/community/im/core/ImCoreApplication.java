package com.nowcoder.community.im.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ImCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(ImCoreApplication.class, args);
    }
}
