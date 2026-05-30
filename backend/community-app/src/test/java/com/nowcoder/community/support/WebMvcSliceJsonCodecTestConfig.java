package com.nowcoder.community.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodec;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class WebMvcSliceJsonCodecTestConfig {

    @Bean
    JsonCodec jsonCodec(ObjectMapper objectMapper) {
        return new JacksonJsonCodec(objectMapper);
    }
}
