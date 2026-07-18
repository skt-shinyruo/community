package com.nowcoder.community.common.web.autoconfig;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ServletWebInfraAutoConfigurationTest {

    @Test
    void shouldCreateOneClientIpResolverWithSanitizedStartupSummary() {
        Logger logger = (Logger) LoggerFactory.getLogger(ClientIpResolver.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            new WebApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(
                            JacksonAutoConfiguration.class,
                            WebInfraAutoConfiguration.class,
                            ServletWebInfraAutoConfiguration.class
                    ))
                    .withPropertyValues(
                            "community.web.trusted-proxy.enabled=true",
                            "community.web.trusted-proxy.cidrs[0]=10.25.0.0/16",
                            "community.web.trusted-proxy.cidrs[1]=2001:db8:25::/48",
                            "community.web.trusted-proxy.source=compose-environment"
                    )
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context).hasSingleBean(ClientIpResolver.class);
                        assertThat(appender.list).hasSize(1);

                        ILoggingEvent event = appender.list.get(0);
                        assertThat(event.getLevel()).isEqualTo(Level.INFO);
                        assertThat(event.getFormattedMessage())
                                .contains("enabled=true", "source=compose-environment", "cidrCount=2")
                                .doesNotContain("10.25.0.0/16", "2001:db8:25::/48");
                    });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
