package com.nowcoder.community.im.core.controller;

import com.nowcoder.community.im.core.application.ConversationApplicationService;
import com.nowcoder.community.im.core.application.PrivateMessageApplicationService;
import com.nowcoder.community.im.core.application.RoomApplicationService;
import com.nowcoder.community.im.core.application.RoomMessageApplicationService;
import com.nowcoder.community.im.core.application.UnreadApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ImCoreControllerBoundaryTest {

    private static final List<Class<?>> CONTROLLERS = List.of(
            ConversationController.class,
            RoomController.class,
            UnreadController.class,
            InternalRealtimeProjectionController.class
    );

    private static final List<Class<?>> APPLICATION_SERVICES = List.of(
            ConversationApplicationService.class,
            PrivateMessageApplicationService.class,
            RoomApplicationService.class,
            RoomMessageApplicationService.class,
            UnreadApplicationService.class
    );

    @Test
    void publicHttpControllersShouldOnlyDependOnApplicationServices() {
        List<String> violations = CONTROLLERS.stream()
                .flatMap(controller -> dependenciesOf(controller).stream()
                        .filter(ImCoreControllerBoundaryTest::isForbiddenDependency)
                        .map(type -> controller.getSimpleName() + " -> " + type.getName()))
                .toList();

        assertThat(violations)
                .as("public IM core controllers must enter use cases through application services")
                .isEmpty();
    }

    @Test
    void applicationServicesShouldNotDependOnTransportOrInfrastructureDetails() {
        List<String> violations = APPLICATION_SERVICES.stream()
                .flatMap(applicationService -> dependenciesOf(applicationService).stream()
                        .filter(ImCoreControllerBoundaryTest::isForbiddenApplicationDependency)
                        .map(type -> applicationService.getSimpleName() + " -> " + type.getName()))
                .toList();

        assertThat(violations)
                .as("IM core application services may use domain repository interfaces and ports, not JdbcTemplate/root service/root repository/infrastructure details")
                .isEmpty();
    }

    @Test
    void productionCodeShouldNotAddRootServiceOrRepositoryPackages() throws IOException {
        List<String> violations = Files.walk(Path.of("src/main/java/com/nowcoder/community/im/core"))
                .filter(Files::isRegularFile)
                .map(Path::toString)
                .filter(path -> path.contains("/core/service/") || path.contains("/core/repository/"))
                .toList();

        assertThat(violations)
                .as("IM core business code should use application/domain/infrastructure tactical packages, not root service/repository packages")
                .isEmpty();
    }

    private static List<Class<?>> dependenciesOf(Class<?> controller) {
        List<Class<?>> fieldTypes = Arrays.stream(controller.getDeclaredFields())
                .map(Field::getType)
                .toList();
        List<Class<?>> constructorTypes = Arrays.stream(controller.getDeclaredConstructors())
                .flatMap(constructor -> Arrays.stream(parameterTypesOf(constructor)))
                .toList();
        return java.util.stream.Stream.concat(fieldTypes.stream(), constructorTypes.stream())
                .distinct()
                .toList();
    }

    private static Class<?>[] parameterTypesOf(Constructor<?> constructor) {
        return constructor.getParameterTypes();
    }

    private static boolean isForbiddenDependency(Class<?> type) {
        String packageName = type.getPackageName();
        return JdbcTemplate.class.equals(type)
                || packageName.equals("com.nowcoder.community.im.core.service")
                || packageName.equals("com.nowcoder.community.im.core.repository");
    }

    private static boolean isForbiddenApplicationDependency(Class<?> type) {
        String packageName = type.getPackageName();
        return JdbcTemplate.class.equals(type)
                || packageName.equals("com.nowcoder.community.im.core.service")
                || packageName.equals("com.nowcoder.community.im.core.repository")
                || packageName.startsWith("com.nowcoder.community.im.core.infrastructure.");
    }
}
