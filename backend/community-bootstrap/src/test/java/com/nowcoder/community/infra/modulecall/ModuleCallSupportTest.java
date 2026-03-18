package com.nowcoder.community.infra.modulecall;

import com.nowcoder.community.contracts.api.CommonErrorCode;
import com.nowcoder.community.contracts.api.Result;
import com.nowcoder.community.contracts.exception.BusinessException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModuleCallSupportTest {

    @Test
    void callResultSuccessShouldReturnValueAndRecordMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        String module = "user";
        String api = "getById";

        String v = ModuleCallSupport.callResult(registry, module, api, () -> Result.ok("ok"));
        assertThat(v).isEqualTo("ok");

        Counter c = registry.find("internal_call_requests_total")
                .tags("module", module, "api", api, "outcome", ModuleCallSupport.OUTCOME_SUCCESS)
                .counter();
        assertThat(c).isNotNull();
        assertThat(c.count()).isEqualTo(1.0);

        assertThat(registry.find("internal_call_requests_total")
                .tags("target", module, "api", api, "outcome", ModuleCallSupport.OUTCOME_SUCCESS)
                .counter()).isNull();

        assertThat(registry.find("internal_call_requests_total")
                .tags("client", module, "api", api, "outcome", ModuleCallSupport.OUTCOME_SUCCESS)
                .counter()).isNull();
    }

    @Test
    void callResultAndThenSuccessShouldMapValueAndRecordMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        String module = "content";
        String api = "resolveEntity";

        String v = ModuleCallSupport.callResultAndThen(
                registry,
                module,
                api,
                () -> Result.ok("ok"),
                s -> s + "!"
        );
        assertThat(v).isEqualTo("ok!");

        assertThat(registry.find("internal_call_requests_total")
                .tags("module", module, "api", api, "outcome", ModuleCallSupport.OUTCOME_SUCCESS)
                .counter()
                .count()).isEqualTo(1.0);
    }

    @Test
    void callResultAndThenValidationFailureShouldRecordUnavailableOutcome() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        String module = "content";
        String api = "resolveEntity";

        BusinessException validation = new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "incomplete");
        assertThatThrownBy(() -> ModuleCallSupport.callResultAndThen(
                registry,
                module,
                api,
                () -> Result.ok("ok"),
                s -> {
                    throw validation;
                }
        ))
                .isSameAs(validation);

        assertThat(registry.find("internal_call_requests_total")
                .tags("module", module, "api", api, "outcome", ModuleCallSupport.OUTCOME_UNAVAILABLE)
                .counter()
                .count()).isEqualTo(1.0);
    }

    @Test
    void timeoutShouldMapToServiceUnavailableAndRecordTimeoutOutcome() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        String module = "content";
        String api = "scanPosts";

        RuntimeException timeout = new RuntimeException(new SocketTimeoutException("read timed out"));
        assertThatThrownBy(() -> ModuleCallSupport.call(registry, module, api, () -> {
            throw timeout;
        }))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode()).isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);
                });

        assertThat(registry.find("internal_call_requests_total")
                .tags("module", module, "api", api, "outcome", ModuleCallSupport.OUTCOME_TIMEOUT)
                .counter()
                .count()).isEqualTo(1.0);
    }

    @Test
    void connectionErrorShouldMapToServiceUnavailableAndRecordUnavailableOutcome() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        String module = "social";
        String api = "entityLikeCount";

        RuntimeException conn = new RuntimeException(new ConnectException("connection refused"));
        assertThatThrownBy(() -> ModuleCallSupport.call(registry, module, api, () -> {
            throw conn;
        }))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode()).isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);
                });

        assertThat(registry.find("internal_call_requests_total")
                .tags("module", module, "api", api, "outcome", ModuleCallSupport.OUTCOME_UNAVAILABLE)
                .counter()
                .count()).isEqualTo(1.0);
    }

    @Test
    void unknownRuntimeShouldMapToInternalErrorAndRecordErrorOutcome() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        String module = "user";
        String api = "getStatus";

        RuntimeException boom = new IllegalStateException("boom");
        assertThatThrownBy(() -> ModuleCallSupport.call(registry, module, api, () -> {
            throw boom;
        }))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode()).isEqualTo(CommonErrorCode.INTERNAL_ERROR);
                });

        assertThat(registry.find("internal_call_requests_total")
                .tags("module", module, "api", api, "outcome", ModuleCallSupport.OUTCOME_ERROR)
                .counter()
                .count()).isEqualTo(1.0);
    }

    @Test
    void businessExceptionForbiddenShouldBeRethrownAndRecordForbiddenOutcome() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        String module = "user";
        String api = "privateApi";

        BusinessException forbidden = new BusinessException(CommonErrorCode.FORBIDDEN, "forbidden");
        assertThatThrownBy(() -> ModuleCallSupport.call(registry, module, api, () -> {
            throw forbidden;
        }))
                .isSameAs(forbidden);

        assertThat(registry.find("internal_call_requests_total")
                .tags("module", module, "api", api, "outcome", ModuleCallSupport.OUTCOME_FORBIDDEN)
                .counter()
                .count()).isEqualTo(1.0);
    }

    @Test
    void failOpenShouldReturnFallbackAndRecordDegradedOutcome() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        String module = "content";
        String api = "scanPosts";

        String v = ModuleCallSupport.call(
                registry,
                module,
                api,
                () -> {
                    throw new IllegalStateException("boom");
                },
                ModuleCallOptions.failOpen(() -> "fallback")
        );
        assertThat(v).isEqualTo("fallback");

        assertThat(registry.find("internal_call_requests_total")
                .tags("module", module, "api", api, "outcome", ModuleCallSupport.OUTCOME_DEGRADED)
                .counter()
                .count()).isEqualTo(1.0);
    }

    @Test
    void resultErrorShouldBeRethrownAndRecordRemoteErrorOutcome() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        String module = "content";
        String api = "scanPosts";

        assertThatThrownBy(() -> ModuleCallSupport.callResult(registry, module, api, () -> Result.error(CommonErrorCode.INVALID_ARGUMENT)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode().getHttpStatus()).isEqualTo(400);
                });

        assertThat(registry.find("internal_call_requests_total")
                .tags("module", module, "api", api, "outcome", ModuleCallSupport.OUTCOME_REMOTE_ERROR)
                .counter()
                .count()).isEqualTo(1.0);
    }

    @Test
    void unwrapResponseEntityShouldUseResultHttpStatusWhenHttpIs2xx() {
        ResponseEntity<Result<Void>> response = ResponseEntity.ok(Result.error(11001, "用户不存在", 404));

        assertThatThrownBy(() -> ModuleCallSupport.unwrap(response, "user"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode().getCode()).isEqualTo(11001);
                    assertThat(be.getErrorCode().getHttpStatus()).isEqualTo(404);
                });
    }
}
