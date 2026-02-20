package com.nowcoder.community.ops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.nowcoder.community")
public class OpsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpsServiceApplication.class, args);
    }
}

