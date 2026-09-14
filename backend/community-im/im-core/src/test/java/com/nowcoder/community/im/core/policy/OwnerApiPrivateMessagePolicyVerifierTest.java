package com.nowcoder.community.im.core.policy;

import com.nowcoder.community.common.security.jwt.JwtCodecs;
import com.nowcoder.community.common.security.jwt.JwtProperties;
import com.nowcoder.community.im.common.policy.PrivateMessagePolicyDecision;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OwnerApiPrivateMessagePolicyVerifierTest {

    @Test
    void verifyShouldCacheOwnerRejectionUntilTtlExpires() throws Exception {
        RecordingRequestFactory requestFactory = new RecordingRequestFactory(
                """
                        {"allowed":false,"code":403,"reasonCode":"policy_denied","message":"用户已拉黑","decidedAtEpochMs":2}
                        """,
                """
                        {"allowed":true,"code":0,"reasonCode":"allowed","message":"","decidedAtEpochMs":3}
                        """
        );
        JwtProperties jwtProperties = jwtProperties();
        OwnerApiPrivateMessagePolicyVerifier verifier = new OwnerApiPrivateMessagePolicyVerifier(
                RestClient.builder()
                        .baseUrl("http://community-app")
                        .requestFactory(requestFactory)
                        .build(),
                properties(Duration.ofMillis(200)),
                jwtProperties
        );
        UUID fromUserId = uuid(1);
        UUID toUserId = uuid(2);

        PrivateMessagePolicyDecision first = verifier.verify(fromUserId, toUserId);
        PrivateMessagePolicyDecision second = verifier.verify(fromUserId, toUserId);
        Thread.sleep(250);
        PrivateMessagePolicyDecision third = verifier.verify(fromUserId, toUserId);

        assertThat(first.allowed()).isFalse();
        assertThat(second).isEqualTo(first);
        assertThat(third.allowed()).isTrue();
        assertThat(requestFactory.requests()).hasSize(2);
        assertThat(requestFactory.requests().get(0).uri().getPath())
                .isEqualTo("/internal/im/realtime/projections/private-message-decision");
        assertThat(requestFactory.requests().get(0).uri().getQuery())
                .contains("fromUserId=" + fromUserId)
                .contains("toUserId=" + toUserId);
        String authorization = requestFactory.requests().get(0).headers().getFirst(HttpHeaders.AUTHORIZATION);
        assertThat(authorization).startsWith("Bearer ");

        Jwt serviceToken = JwtCodecs.serviceTokenDecoder(jwtProperties, "community-auth", "community-app")
                .decode(authorization.substring("Bearer ".length()));
        assertThat(serviceToken.getHeaders())
                .containsEntry("alg", "HS256")
                .containsEntry("typ", JwtCodecs.SERVICE_TOKEN_TYPE);
        assertThat(serviceToken.getAudience()).containsExactly("community-app");
        assertThat(serviceToken.getClaimAsString("scope")).isEqualTo("im.realtime.internal");
    }

    @Test
    void verifyShouldFailClosedWithoutCachingWhenOwnerReturnsServerError() {
        RecordingRequestFactory requestFactory = new RecordingRequestFactory(List.of(
                Step.respond(HttpStatus.INTERNAL_SERVER_ERROR, ""),
                Step.respond(HttpStatus.OK, """
                        {"allowed":true,"code":0,"reasonCode":"allowed","message":"","decidedAtEpochMs":3}
                        """)
        ));
        OwnerApiPrivateMessagePolicyVerifier verifier = new OwnerApiPrivateMessagePolicyVerifier(
                RestClient.builder()
                        .baseUrl("http://community-app")
                        .requestFactory(requestFactory)
                        .build(),
                properties(Duration.ofMillis(200)),
                jwtProperties()
        );

        assertThatThrownBy(() -> verifier.verify(uuid(1), uuid(2)))
                .isInstanceOf(RestClientResponseException.class);

        PrivateMessagePolicyDecision afterRecovery = verifier.verify(uuid(1), uuid(2));

        assertThat(afterRecovery.allowed()).isTrue();
        assertThat(requestFactory.requests()).hasSize(2);
    }

    @Test
    void verifyShouldFailClosedWhenOwnerIsUnreachable() {
        RecordingRequestFactory requestFactory = new RecordingRequestFactory(List.of(
                Step.fail(new IOException("connect timed out"))
        ));
        OwnerApiPrivateMessagePolicyVerifier verifier = new OwnerApiPrivateMessagePolicyVerifier(
                RestClient.builder()
                        .baseUrl("http://community-app")
                        .requestFactory(requestFactory)
                        .build(),
                properties(Duration.ofMillis(200)),
                jwtProperties()
        );

        assertThatThrownBy(() -> verifier.verify(uuid(1), uuid(2)))
                .isInstanceOf(ResourceAccessException.class);
    }

    private static ImCorePolicyClientProperties properties(Duration ttl) {
        ImCorePolicyClientProperties properties = new ImCorePolicyClientProperties();
        properties.setRejectionCacheTtl(ttl);
        properties.setRequestTimeout(Duration.ofMillis(100));
        properties.setInternalScope("im.realtime.internal");
        return properties;
    }

    private static JwtProperties jwtProperties() {
        JwtProperties properties = new JwtProperties();
        properties.setServiceHmacSecret("im-core-test-jwt-secret-at-least-32b");
        properties.setIssuer("community-auth");
        return properties;
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }

    private record RecordedRequest(URI uri, HttpHeaders headers) {
    }

    private record Step(HttpStatus status, String body, IOException failure) {

        private static Step respond(HttpStatus status, String body) {
            return new Step(status, body, null);
        }

        private static Step fail(IOException failure) {
            return new Step(null, null, failure);
        }
    }

    private static final class RecordingRequestFactory implements ClientHttpRequestFactory {

        private final List<Step> steps;
        private final List<RecordedRequest> requests = new ArrayList<>();

        private RecordingRequestFactory(String... responseBodies) {
            this(Arrays.stream(responseBodies)
                    .map(body -> Step.respond(HttpStatus.OK, body))
                    .toList());
        }

        private RecordingRequestFactory(List<Step> steps) {
            this.steps = steps;
        }

        @Override
        public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod) {
            return new RecordingClientHttpRequest(uri);
        }

        List<RecordedRequest> requests() {
            return requests;
        }

        private final class RecordingClientHttpRequest implements ClientHttpRequest {

            private final URI uri;
            private final HttpHeaders headers = new HttpHeaders();
            private final Map<String, Object> attributes = new HashMap<>();

            private RecordingClientHttpRequest(URI uri) {
                this.uri = uri;
            }

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
                return HttpMethod.GET;
            }

            @Override
            public URI getURI() {
                return uri;
            }

            @Override
            public ClientHttpResponse execute() throws IOException {
                requests.add(new RecordedRequest(uri, HttpHeaders.copyOf(headers)));
                Step step = steps.get(Math.min(requests.size() - 1, steps.size() - 1));
                if (step.failure() != null) {
                    throw step.failure();
                }
                MockClientHttpResponse response = new MockClientHttpResponse(
                        step.body().getBytes(),
                        step.status()
                );
                response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                return response;
            }
        }
    }
}
