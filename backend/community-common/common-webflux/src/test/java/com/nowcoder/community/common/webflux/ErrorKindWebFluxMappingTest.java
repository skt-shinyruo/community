package com.nowcoder.community.common.webflux;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.ErrorCode;
import com.nowcoder.community.common.web.Result;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorKindWebFluxMappingTest {

    @ParameterizedTest(name = "{0} -> HTTP {1}")
    @MethodSource("kindMappings")
    void businessErrorsShouldMapKindToStableStatusAndResultBody(String kindName, int expectedStatus) throws Exception {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ErrorCode errorCode = errorCode(kindName, 99002, "mapped reactive error");
        Method handleBusiness = Arrays.stream(GlobalExceptionHandler.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("handleBusiness"))
                .filter(method -> Arrays.equals(method.getParameterTypes(), new Class<?>[]{BusinessException.class}))
                .findFirst()
                .orElse(null);

        assertThat(handleBusiness)
                .as("WebFlux must handle BusinessException through ErrorKind")
                .isNotNull();

        @SuppressWarnings("unchecked")
        ResponseEntity<Result<Void>> response = (ResponseEntity<Result<Void>>) handleBusiness.invoke(
                handler,
                new BusinessException(errorCode)
        );
        assertThat(response.getStatusCode().value()).isEqualTo(expectedStatus);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(99002);
        assertThat(response.getBody().getMessage()).isEqualTo("mapped reactive error");
        assertThat(response.getBody().getHttpStatus()).isEqualTo(expectedStatus);
    }

    private static Stream<Arguments> kindMappings() {
        return Stream.of(
                Arguments.of("INVALID_INPUT", 400),
                Arguments.of("UNAUTHENTICATED", 401),
                Arguments.of("FORBIDDEN", 403),
                Arguments.of("NOT_FOUND", 404),
                Arguments.of("CONFLICT", 409),
                Arguments.of("THROTTLED", 429),
                Arguments.of("UNAVAILABLE", 503),
                Arguments.of("INTERNAL", 500)
        );
    }

    private static ErrorCode errorCode(String kindName, int code, String message) {
        return (ErrorCode) Proxy.newProxyInstance(
                ErrorCode.class.getClassLoader(),
                new Class<?>[]{ErrorCode.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getCode" -> code;
                    case "getMessage" -> message;
                    case "getKind" -> errorKind(kindName);
                    case "getHttpStatus" -> throw new AssertionError(
                            "WebFlux adapter must map ErrorKind and must not ask ErrorCode for HTTP status"
                    );
                    case "toString" -> "TestErrorCode[" + kindName + "]";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.toString());
                }
        );
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object errorKind(String kindName) throws Exception {
        Class<? extends Enum> type = Class.forName("com.nowcoder.community.common.exception.ErrorKind")
                .asSubclass(Enum.class);
        return Enum.valueOf(type, kindName);
    }
}
