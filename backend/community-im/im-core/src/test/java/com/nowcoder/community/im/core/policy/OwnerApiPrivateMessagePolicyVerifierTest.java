package com.nowcoder.community.im.core.policy;

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
import org.springframework.web.client.RestClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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
        OwnerApiPrivateMessagePolicyVerifier verifier = new OwnerApiPrivateMessagePolicyVerifier(
                RestClient.builder()
                        .baseUrl("http://community-app")
                        .requestFactory(requestFactory)
                        .build(),
                properties(Duration.ofMillis(200)),
                jwtProperties()
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
        assertThat(requestFactory.requests().get(0).headers().getFirst(HttpHeaders.AUTHORIZATION))
                .startsWith("Bearer ");
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
        properties.setHmacSecret("im-core-test-jwt-secret-at-least-32b");
        properties.setIssuer("community-auth");
        return properties;
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }

    private record RecordedRequest(URI uri, HttpHeaders headers) {
    }

    private static final class RecordingRequestFactory implements ClientHttpRequestFactory {

        private final List<String> responseBodies;
        private final List<RecordedRequest> requests = new ArrayList<>();

        private RecordingRequestFactory(String... responseBodies) {
            this.responseBodies = List.of(responseBodies);
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
            public HttpMethod getMethod() {
                return HttpMethod.GET;
            }

            @Override
            public URI getURI() {
                return uri;
            }

            @Override
            public ClientHttpResponse execute() throws IOException {
                requests.add(new RecordedRequest(uri, HttpHeaders.writableHttpHeaders(headers)));
                int index = Math.min(requests.size() - 1, responseBodies.size() - 1);
                MockClientHttpResponse response = new MockClientHttpResponse(
                        responseBodies.get(index).getBytes(),
                        HttpStatus.OK
                );
                response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                return response;
            }
        }
    }
}
