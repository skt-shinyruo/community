package com.nowcoder.community.infra.job;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@AutoConfiguration
@ConditionalOnClass(XxlJobSpringExecutor.class)
@EnableConfigurationProperties(XxlJobProperties.class)
public class XxlJobAutoConfiguration {

    private static final int DEFAULT_LOG_RETENTION_DAYS = 30;

    @Bean
    @ConditionalOnProperty(prefix = "xxl.job", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(XxlJobSpringExecutor.class)
    public XxlJobSpringExecutor xxlJobSpringExecutor(XxlJobProperties properties) {
        validateRequiredProperties(properties);

        XxlJobProperties.Admin admin = properties.getAdmin();
        XxlJobProperties.Executor executor = properties.getExecutor();

        XxlJobSpringExecutor xxlJobSpringExecutor = new XxlJobSpringExecutor();
        xxlJobSpringExecutor.setAdminAddresses(admin.getAddresses().trim());
        xxlJobSpringExecutor.setAppname(executor.getAppname().trim());
        xxlJobSpringExecutor.setAccessToken(admin.getAccessToken().trim());
        String executorAddress = trimToNull(executor.getAddress());
        if (executorAddress != null) {
            xxlJobSpringExecutor.setAddress(executorAddress);
            xxlJobSpringExecutor.setPort(parseExecutorAddressPort(executorAddress));
        }
        xxlJobSpringExecutor.setLogPath(defaultLogPath());
        xxlJobSpringExecutor.setLogRetentionDays(DEFAULT_LOG_RETENTION_DAYS);
        return xxlJobSpringExecutor;
    }

    private static void validateRequiredProperties(XxlJobProperties properties) {
        List<String> missing = new ArrayList<>();
        if (properties == null || !StringUtils.hasText(properties.getAdmin().getAddresses())) {
            missing.add("xxl.job.admin.addresses");
        }
        if (properties == null || !StringUtils.hasText(properties.getAdmin().getAccessToken())) {
            missing.add("xxl.job.admin.accessToken");
        }
        if (properties == null || !StringUtils.hasText(properties.getExecutor().getAppname())) {
            missing.add("xxl.job.executor.appname");
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "xxl.job.enabled=true requires non-blank properties: " + String.join(", ", missing)
            );
        }
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static int parseExecutorAddressPort(String address) {
        try {
            URI uri = new URI(address);
            if (!StringUtils.hasText(uri.getScheme()) || !StringUtils.hasText(uri.getHost()) || uri.getPort() <= 0) {
                throw invalidExecutorAddress();
            }
            return uri.getPort();
        } catch (URISyntaxException e) {
            throw invalidExecutorAddress();
        }
    }

    private static IllegalStateException invalidExecutorAddress() {
        return new IllegalStateException(
                "xxl.job.executor.address must be a valid URI with an explicit positive port"
        );
    }

    private static String defaultLogPath() {
        return Path.of(System.getProperty("java.io.tmpdir"), "community-app", "xxl-job").toString();
    }
}
