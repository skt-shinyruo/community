package com.nowcoder.community.common.webflux;

import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.web.Result;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    @Test
    void handleGeneric_shouldRenderInternalErrorResult() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<Result<Void>> response = handler.handleGeneric(new RuntimeException("boom"));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(CommonErrorCode.INTERNAL_ERROR.getCode());
    }
}
