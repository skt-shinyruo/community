package com.nowcoder.community.infra.startup;

import org.springframework.core.env.Environment;

import java.util.Arrays;

public final class ProductionEnvironmentPredicate {

    private ProductionEnvironmentPredicate() {
    }

    public static boolean isProduction(Environment environment) {
        if (environment == null) {
            return false;
        }
        if (Arrays.stream(environment.getActiveProfiles())
                .anyMatch(ProductionEnvironmentPredicate::isProductionName)) {
            return true;
        }
        return isProductionName(environment.getProperty("DEPLOYMENT_ENVIRONMENT"))
                || isProductionName(environment.getProperty("deployment.environment"))
                || isProductionName(environment.getProperty(
                        "spring.cloud.nacos.discovery.metadata.deployment.environment"));
    }

    private static boolean isProductionName(String value) {
        return "prod".equalsIgnoreCase(value) || "production".equalsIgnoreCase(value);
    }
}
