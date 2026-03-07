package com.nowcoder.community.infra.internalclient;

import com.nowcoder.community.contracts.api.CommonErrorCode;
import com.nowcoder.community.contracts.api.Result;
import com.nowcoder.community.contracts.exception.BusinessException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InternalClientSupportTest {

    @Test
    void callResultSuccessShouldReturnValueAndRecordMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        String target = "user-service";
        String api = "getById";

        String v = InternalClientSupport.callResult(registry, target, api, () -> Result.ok("ok"));
        assertThat(v).isEqualTo("ok");

        Counter c = registry.find("internal_call_requests_total")
                .tags("target", target, "api", api, "outcome", InternalClientSupport.OUTCOME_SUCCESS)
                .counter();
        assertThat(c).isNotNull();
        assertThat(c.count()).isEqualTo(1.0);

        assertThat(registry.find("internal_call_requests_total")
                .tags("client", target, "api", api, "outcome", InternalClientSupport.OUTCOME_SUCCESS)
                .counter()).isNull();
    }

    @Test
    void callResultAndThenSuccessShouldMapValueAndRecordMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        String target = "content-service";
        String api = "resolveEntity";

        String v = InternalClientSupport.callResultAndThen(
                registry,
                target,
                api,
                () -> Result.ok("ok"),
                s -> s + "!"
        );
        assertThat(v).isEqualTo("ok!");

        assertThat(registry.find("internal_call_requests_total")
                .tags("target", target, "api", api, "outcome", InternalClientSupport.OUTCOME_SUCCESS)
                .counter()
                .count()).isEqualTo(1.0);
    }

    @Test
    void callResultAndThenValidationFailureShouldRecordUnavailableOutcome() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        String target = "content-service";
        String api = "resolveEntity";

        BusinessException validation = new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "incomplete");
        assertThatThrownBy(() -> InternalClientSupport.callResultAndThen(
                registry,
                target,
                api,
                () -> Result.ok("ok"),
                s -> {
                    throw validation;
                }
        ))
                .isSameAs(validation);

        assertThat(registry.find("internal_call_requests_total")
                .tags("target", target, "api", api, "outcome", InternalClientSupport.OUTCOME_UNAVAILABLE)
                .counter()
                .count()).isEqualTo(1.0);
    }

    @Test
    void timeoutShouldMapToServiceUnavailableAndRecordTimeoutOutcome() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        String target = "content-service";
        String api = "scanPosts";

        RuntimeException timeout = new RuntimeException(new SocketTimeoutException("read timed out"));
        assertThatThrownBy(() -> InternalClientSupport.call(registry, target, api, () -> {
            throw timeout;
        }))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode()).isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);
                });

        assertThat(registry.find("internal_call_requests_total")
                .tags("target", target, "api", api, "outcome", InternalClientSupport.OUTCOME_TIMEOUT)
                .counter()
                .count()).isEqualTo(1.0);
    }

    @Test
    void connectionErrorShouldMapToServiceUnavailableAndRecordUnavailableOutcome() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        String target = "social-service";
        String api = "entityLikeCount";

        RuntimeException conn = new RuntimeException(new ConnectException("connection refused"));
        assertThatThrownBy(() -> InternalClientSupport.call(registry, target, api, () -> {
            throw conn;
        }))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode()).isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);
                });

        assertThat(registry.find("internal_call_requests_total")
                .tags("target", target, "api", api, "outcome", InternalClientSupport.OUTCOME_UNAVAILABLE)
                .counter()
                .count()).isEqualTo(1.0);
    }

    @Test
    void unknownRuntimeShouldMapToInternalErrorAndRecordErrorOutcome() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        String target = "user-service";
        String api = "getStatus";

        RuntimeException boom = new IllegalStateException("boom");
        assertThatThrownBy(() -> InternalClientSupport.call(registry, target, api, () -> {
            throw boom;
        }))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode()).isEqualTo(CommonErrorCode.INTERNAL_ERROR);
                });

        assertThat(registry.find("internal_call_requests_total")
                .tags("target", target, "api", api, "outcome", InternalClientSupport.OUTCOME_ERROR)
                .counter()
                .count()).isEqualTo(1.0);
    }

    @Test
    void businessExceptionForbiddenShouldBeRethrownAndRecordForbiddenOutcome() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        String target = "user-service";
        String api = "privateApi";

        BusinessException forbidden = new BusinessException(CommonErrorCode.FORBIDDEN, "forbidden");
        assertThatThrownBy(() -> InternalClientSupport.call(registry, target, api, () -> {
            throw forbidden;
        }))
                .isSameAs(forbidden);

        assertThat(registry.find("internal_call_requests_total")
                .tags("target", target, "api", api, "outcome", InternalClientSupport.OUTCOME_FORBIDDEN)
                .counter()
                .count()).isEqualTo(1.0);
    }

    @Test
    void failOpenShouldReturnFallbackAndRecordDegradedOutcome() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        String target = "content-service";
        String api = "scanPosts";

        String v = InternalClientSupport.call(
                registry,
                target,
                api,
                () -> {
                    throw new IllegalStateException("boom");
                },
                InternalCallOptions.failOpen(() -> "fallback")
        );
        assertThat(v).isEqualTo("fallback");

        assertThat(registry.find("internal_call_requests_total")
                .tags("target", target, "api", api, "outcome", InternalClientSupport.OUTCOME_DEGRADED)
                .counter()
                .count()).isEqualTo(1.0);
    }

    @Test
    void resultErrorShouldBeRethrownAndRecordRemoteErrorOutcome() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        String target = "content-service";
        String api = "scanPosts";

        assertThatThrownBy(() -> InternalClientSupport.callResult(registry, target, api, () -> Result.error(CommonErrorCode.INVALID_ARGUMENT)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode().getHttpStatus()).isEqualTo(400);
                });

        assertThat(registry.find("internal_call_requests_total")
                .tags("target", target, "api", api, "outcome", InternalClientSupport.OUTCOME_REMOTE_ERROR)
                .counter()
                .count()).isEqualTo(1.0);
    }
}

