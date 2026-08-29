package com.nowcoder.community.common.exception;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeKindContractTest {

    private static final Set<String> REQUIRED_KINDS = Set.of(
            "INVALID_INPUT",
            "UNAUTHENTICATED",
            "FORBIDDEN",
            "NOT_FOUND",
            "CONFLICT",
            "THROTTLED",
            "UNAVAILABLE",
            "INTERNAL"
    );

    @Test
    void errorCodeShouldExposeTransportNeutralKindInsteadOfHttpStatus() {
        Set<String> methodNames = Arrays.stream(ErrorCode.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertThat(methodNames)
                .contains("getCode", "getMessage", "getKind")
                .doesNotContain("getHttpStatus");
    }

    @Test
    void errorKindShouldDefineTheCompleteStableCategorySet() throws Exception {
        Class<?> errorKind = Class.forName("com.nowcoder.community.common.exception.ErrorKind");

        assertThat(errorKind.isEnum()).isTrue();
        assertThat(Arrays.stream(errorKind.getEnumConstants())
                .map(Object::toString)
                .collect(Collectors.toSet()))
                .containsExactlyInAnyOrderElementsOf(REQUIRED_KINDS);
    }


}
