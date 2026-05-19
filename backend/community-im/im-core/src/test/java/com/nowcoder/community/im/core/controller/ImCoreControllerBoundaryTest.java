package com.nowcoder.community.im.core.controller;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
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
}
