package com.nowcoder.community.common.web.autoconfig;

import com.nowcoder.community.common.web.CommonJacksonConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import(CommonJacksonConfig.class)
public class WebInfraAutoConfiguration {
}
