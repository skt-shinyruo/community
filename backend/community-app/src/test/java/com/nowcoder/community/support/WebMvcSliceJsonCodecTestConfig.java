package com.nowcoder.community.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class WebMvcSliceJsonCodecTestConfig {

    @Bean
    JacksonJsonCodec jsonCodec(ObjectMapper objectMapper) {
        return new JacksonJsonCodec(objectMapper);
    }
}
