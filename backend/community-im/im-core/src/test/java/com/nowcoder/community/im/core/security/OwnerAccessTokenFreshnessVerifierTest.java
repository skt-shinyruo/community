package com.nowcoder.community.im.core.security;

import com.nowcoder.community.im.core.application.AccessTokenFreshnessApplicationService.Decision;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class OwnerAccessTokenFreshnessVerifierTest {

    @Test
    void verifyShouldForwardTheAccessTokenAndMapOwnerResponses() {
        RecordingRequestFactory requestFactory = new RecordingRequestFactory(HttpStatus.OK);
        OwnerAccessTokenFreshnessVerifier verifier = new OwnerAccessTokenFreshnessVerifier(
                RestClient.builder().baseUrl("http://community-app").requestFactory(requestFactory).build()
        );

        assertThat(verifier.verify("access-token")).isEqualTo(Decision.FRESH);
        assertThat(requestFactory.path()).hasValue("/api/auth/me");
        assertThat(requestFactory.authorization()).hasValue("Bearer access-token");

        assertDecision(HttpStatus.UNAUTHORIZED, Decision.STALE);
        assertDecision(HttpStatus.FORBIDDEN, Decision.DENIED);
        assertDecision(HttpStatus.SERVICE_UNAVAILABLE, Decision.UNAVAILABLE);
    }

    @Test
    void verifyShouldFailClosedWhenTheOwnerCallThrows() {
        RestClient restClient = RestClient.builder()
                .baseUrl("http://community-app")
                .requestFactory((uri, method) -> {
                    throw new java.io.IOException("owner unavailable");
                })
                .build();

        assertThat(new OwnerAccessTokenFreshnessVerifier(restClient).verify("access-token"))
                .isEqualTo(Decision.UNAVAILABLE);
    }

    private static void assertDecision(
            HttpStatus status,
            Decision expected
    ) {
        RestClient restClient = RestClient.builder()
                .baseUrl("http://community-app")
                .requestFactory(new RecordingRequestFactory(status))
                .build();
        assertThat(new OwnerAccessTokenFreshnessVerifier(restClient).verify("access-token")).isEqualTo(expected);
    }

    private static final class RecordingRequestFactory implements ClientHttpRequestFactory {

        private final HttpStatus responseStatus;
        private final AtomicReference<String> path = new AtomicReference<>();
        private final AtomicReference<String> authorization = new AtomicReference<>();

        private RecordingRequestFactory(HttpStatus responseStatus) {
            this.responseStatus = responseStatus;
        }

        @Override
        public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod) {
            return new ClientHttpRequest() {
                private final HttpHeaders headers = new HttpHeaders();
                private final Map<String, Object> attributes = new HashMap<>();

                @Override
                public OutputStream getBody() {
                    return new ByteArrayOutputStream();
                }

                @Override
                public HttpHeaders getHeaders() {
                    return headers;
                }

                @Override
                public Map<String, Object> getAttributes() {
                    return attributes;
                }

                @Override
                public HttpMethod getMethod() {
                    return httpMethod;
                }

                @Override
                public URI getURI() {
                    return uri;
                }

                @Override
                public ClientHttpResponse execute() {
                    path.set(uri.getPath());
                    authorization.set(headers.getFirst(HttpHeaders.AUTHORIZATION));
                    return new MockClientHttpResponse(new byte[0], responseStatus);
                }
            };
        }

        AtomicReference<String> path() {
            return path;
        }

        AtomicReference<String> authorization() {
            return authorization;
        }
    }
}
