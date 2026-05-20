package com.nowcoder.community.im.realtime.projection;

import com.nowcoder.community.common.security.jwt.JwtProperties;
import com.nowcoder.community.im.realtime.client.ImServiceClientProperties;
import com.nowcoder.community.im.realtime.session.ImSessionProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class SnapshotClientContractVersionTest {

    @Test
    void membershipSnapshotClientShouldFailRefreshWhenSnapshotUsesFutureSchemaVersion() {
        MembershipSnapshotClient client = new MembershipSnapshotClient(
                webClient("""
                        {
                          "schemaVersion": 2,
                          "entries": [],
                          "nextRoomId": null,
                          "nextUserId": null,
                          "hasMore": false,
                          "snapshotHighWatermark": 100
                        }
                        """),
                clientProperties(),
                sessionProperties(),
                jwtProperties()
        );

        StepVerifier.create(client.fetchSnapshot())
                .expectErrorSatisfies(error ->
                        assertThat(rootMessage(error)).contains("unsupported IM schemaVersion"))
                .verify();
    }

    @Test
    void policySnapshotClientShouldFailRefreshWhenNestedEntryUsesFutureSchemaVersion() {
        PolicySnapshotClient client = new PolicySnapshotClient(
                webClient("""
                        {
                          "schemaVersion": 1,
                          "entries": [
                            {
                              "schemaVersion": 2,
                              "userId": "00000000-0000-7000-8000-000000000001",
                              "userExists": true,
                              "suspended": false,
                              "muted": false,
                              "canSendPrivate": true,
                              "version": 100,
                              "occurredAtEpochMillis": 100
                            }
                          ],
                          "nextUserId": null,
                          "hasMore": false,
                          "snapshotHighWatermark": 100
                        }
                        """),
                clientProperties(),
                sessionProperties(),
                jwtProperties()
        );

        StepVerifier.create(client.fetchUserPolicySnapshot())
                .expectErrorSatisfies(error ->
                        assertThat(rootMessage(error)).contains("unsupported IM schemaVersion"))
                .verify();
    }

    private static WebClient webClient(String body) {
        ExchangeFunction exchange = request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body(body)
                .build());
        return WebClient.builder().exchangeFunction(exchange).build();
    }

    private static ImServiceClientProperties clientProperties() {
        ImServiceClientProperties properties = new ImServiceClientProperties();
        properties.setSnapshotTimeoutMs(3000);
        return properties;
    }

    private static ImSessionProperties sessionProperties() {
        ImSessionProperties properties = new ImSessionProperties();
        properties.setWorkerId("worker-test");
        return properties;
    }

    private static JwtProperties jwtProperties() {
        JwtProperties properties = new JwtProperties();
        properties.setIssuer("community-test");
        properties.setHmacSecret("snapshot-client-contract-version-secret-32b");
        return properties;
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage();
    }
}
