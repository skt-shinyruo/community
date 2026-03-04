package com.nowcoder.community.infra.idempotency.autoconfig;

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotencyAutoConfigurationIsolatedClassLoadingTest {

    @Test
    void baseAutoConfigShouldLoadWithoutJdbcAndRedisOnClasspath() throws Exception {
        IsolatedClassPath isolatedClassPath = IsolatedClassPath.currentTestRuntimeWithoutArtifacts(
                "spring-jdbc-",
                "spring-data-redis-"
        );

        assertThat(isolatedClassPath.excludedEntries())
                .as("sanity: test runtime classpath should contain optional deps we intend to exclude")
                .isNotEmpty();

        try (URLClassLoader classLoader = new URLClassLoader(isolatedClassPath.urls().toArray(URL[]::new), null)) {
            assertThatThrownBy(() -> Class.forName("org.springframework.jdbc.core.JdbcTemplate", false, classLoader))
                    .as("sanity: isolated classloader should not see spring-jdbc")
                    .isInstanceOf(ClassNotFoundException.class);
            assertThatThrownBy(() -> Class.forName("org.springframework.data.redis.core.StringRedisTemplate", false, classLoader))
                    .as("sanity: isolated classloader should not see spring-data-redis")
                    .isInstanceOf(ClassNotFoundException.class);

            String className = IdempotencyAutoConfiguration.class.getName();
            Class<?> autoConfigClass = Class.forName(className, false, classLoader);
            assertThatCode(() -> forceResolveMethodSignatures(autoConfigClass))
                    .doesNotThrowAnyException();
        }
    }

    private static void forceResolveMethodSignatures(Class<?> autoConfigClass) {
        for (var method : autoConfigClass.getDeclaredMethods()) {
            // Spring processes @Bean methods reflectively; force type resolution to simulate missing optional deps.
            method.getParameterTypes();
            method.getReturnType();
        }
    }

    private record IsolatedClassPath(List<URL> urls, List<String> excludedEntries) {

        static IsolatedClassPath currentTestRuntimeWithoutArtifacts(String... excludedNameFragments) throws Exception {
            String classPath = System.getProperty("java.class.path", "");
            String separator = System.getProperty("path.separator", ":");
            String[] entries = classPath.split(Pattern.quote(separator));

            List<URL> urls = new ArrayList<>();
            List<String> excludedEntries = new ArrayList<>();
            for (String entry : entries) {
                if (entry == null || entry.isBlank()) {
                    continue;
                }
                if (shouldExclude(entry, excludedNameFragments)) {
                    excludedEntries.add(entry);
                    continue;
                }
                urls.add(Path.of(entry).toUri().toURL());
            }
            return new IsolatedClassPath(urls, excludedEntries);
        }

        private static boolean shouldExclude(String entry, String[] fragments) {
            if (fragments == null || fragments.length == 0) {
                return false;
            }
            for (String fragment : fragments) {
                if (fragment != null && !fragment.isBlank() && entry.contains(fragment)) {
                    return true;
                }
            }
            return false;
        }
    }
}
