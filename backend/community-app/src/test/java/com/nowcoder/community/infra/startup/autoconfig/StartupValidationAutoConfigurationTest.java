package com.nowcoder.community.infra.startup.autoconfig;

import com.nowcoder.community.infra.startup.StartupValidation;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StartupValidationAutoConfigurationTest {

    @Test
    void productionProfileShouldEnableFailClosedStartupValidation() {
        contextRunner("production").run(context ->
                assertThat(context).hasSingleBean(StartupValidation.class));
    }

    @Test
    void prodProfileShouldEnableFailClosedStartupValidation() {
        contextRunner("prod").run(context ->
                assertThat(context).hasSingleBean(StartupValidation.class));
    }

    @Test
    void uppercaseProdProfileShouldEnableFailClosedStartupValidation() {
        contextRunner("PROD").run(context ->
                assertThat(context).hasSingleBean(StartupValidation.class));
    }

    @Test
    void mixedCaseProductionProfileShouldEnableFailClosedStartupValidation() {
        contextRunner("ProDucTion").run(context ->
                assertThat(context).hasSingleBean(StartupValidation.class));
    }

    @Test
    void developmentProfileShouldNotEnableProductionValidation() {
        contextRunner("dev").run(context ->
                assertThat(context).doesNotHaveBean(StartupValidation.class));
    }

    @Test
    void productionDeploymentEnvironmentShouldEnableValidationEvenWithDevProfile() {
        contextRunner("dev")
                .withPropertyValues("DEPLOYMENT_ENVIRONMENT=production")
                .run(context -> assertThat(context).hasSingleBean(StartupValidation.class));
    }

    @Test
    void productionDeploymentEnvironmentShouldRunFailClosedValidationEvenWithDevProfile() {
        contextRunner("dev")
                .withPropertyValues("DEPLOYMENT_ENVIRONMENT=production")
                .run(context -> {
                    ApplicationRunner runner = context.getBean("startupValidationRunner", ApplicationRunner.class);

                    assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("security.jwt.access-public-key");
                });
    }

    @Test
    void productionDiscoveryMetadataShouldEnableValidationEvenWithDevProfile() {
        contextRunner("dev")
                .withPropertyValues("spring.cloud.nacos.discovery.metadata.deployment.environment=PROD")
                .run(context -> assertThat(context).hasSingleBean(StartupValidation.class));
    }

    private ApplicationContextRunner contextRunner(String profile) {
        return new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles(profile))
                .withUserConfiguration(StartupValidationAutoConfiguration.class);
    }
}
