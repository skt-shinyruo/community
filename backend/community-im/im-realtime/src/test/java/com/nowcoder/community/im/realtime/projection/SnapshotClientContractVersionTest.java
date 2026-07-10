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

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SnapshotClientContractVersionTest {

    @Test
    void membershipSnapshotClientShouldFailRefreshWhenSnapshotOmitsSchemaVersion() {
        MembershipSnapshotClient client = membershipClient(webClient("""
                {
                  "entries": [],
                  "nextRoomId": null,
                  "nextUserId": null,
                  "hasMore": false,
                  "snapshotHighWatermark": 100
                }
                """));

        StepVerifier.create(client.fetchSnapshot())
                .expectErrorSatisfies(error ->
                        assertThat(rootMessage(error)).contains("unsupported IM schemaVersion"))
                .verify();
    }

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

    @Test
    void policySnapshotClientShouldFailRefreshWhenNestedEntryOmitsVersion() {
        PolicySnapshotClient client = policyClient(webClient("""
                {
                  "schemaVersion": 1,
                  "entries": [
                    {
                      "schemaVersion": 1,
                      "userId": "00000000-0000-7000-8000-000000000001",
                      "userExists": true,
                      "suspended": false,
                      "muted": false,
                      "canSendPrivate": true,
                      "occurredAtEpochMillis": 100
                    }
                  ],
                  "nextUserId": null,
                  "hasMore": false,
                  "snapshotHighWatermark": 100
                }
                """));

        StepVerifier.create(client.fetchUserPolicySnapshot())
                .expectErrorSatisfies(error ->
                        assertThat(rootMessage(error)).contains("version must be positive"))
                .verify();
    }

    @Test
    void membershipSnapshotClientShouldFailRefreshWhenSnapshotOmitsWatermark() {
        MembershipSnapshotClient client = membershipClient(webClient("""
                {
                  "schemaVersion": 1,
                  "entries": [],
                  "nextRoomId": null,
                  "nextUserId": null,
                  "hasMore": false
                }
                """));

        StepVerifier.create(client.fetchSnapshot())
                .expectErrorSatisfies(error ->
                        assertThat(rootMessage(error)).contains("snapshotHighWatermark must be non-negative"))
                .verify();
    }

    @Test
    void membershipSnapshotClientShouldReturnExplicitZeroWatermark() {
        MembershipSnapshotClient client = membershipClient(webClient("""
                {
                  "schemaVersion": 1,
                  "entries": [],
                  "nextRoomId": null,
                  "nextUserId": null,
                  "hasMore": false,
                  "snapshotHighWatermark": 0
                }
                """));

        StepVerifier.create(client.fetchSnapshot())
                .assertNext(snapshot -> {
                    assertThat(snapshot.entries()).isEmpty();
                    assertThat(snapshot.snapshotHighWatermark()).isZero();
                })
                .verifyComplete();
    }

    @Test
    void membershipSnapshotClientShouldFailWhenResponseContainsNoPage() {
        MembershipSnapshotClient client = membershipClient(noContentWebClient());

        StepVerifier.create(client.fetchSnapshot())
                .expectErrorMatches(error -> error instanceof IllegalStateException
                        && "projection snapshot returned no pages".equals(error.getMessage()))
                .verify();
    }

    @Test
    void membershipSnapshotClientShouldRejectChangedPaginationWatermark() {
        MembershipSnapshotClient client = membershipClient(webClient(
                """
                        {
                          "schemaVersion": 1,
                          "entries": [],
                          "nextRoomId": "00000000-0000-7000-8000-000000000001",
                          "nextUserId": "00000000-0000-7000-8000-000000000002",
                          "hasMore": true,
                          "snapshotHighWatermark": 10
                        }
                        """,
                """
                        {
                          "schemaVersion": 1,
                          "entries": [],
                          "nextRoomId": null,
                          "nextUserId": null,
                          "hasMore": false,
                          "snapshotHighWatermark": 11
                        }
                        """
        ));

        StepVerifier.create(client.fetchSnapshot())
                .expectErrorMatches(error -> error instanceof IllegalStateException
                        && "projection snapshot watermark changed between pages".equals(error.getMessage()))
                .verify();
    }

    @Test
    void policySnapshotClientShouldRejectChangedPaginationWatermark() {
        PolicySnapshotClient client = policyClient(webClient(
                """
                        {
                          "schemaVersion": 1,
                          "entries": [],
                          "nextUserId": "00000000-0000-7000-8000-000000000001",
                          "hasMore": true,
                          "snapshotHighWatermark": 10
                        }
                        """,
                """
                        {
                          "schemaVersion": 1,
                          "entries": [],
                          "nextUserId": null,
                          "hasMore": false,
                          "snapshotHighWatermark": 11
                        }
                        """
        ));

        StepVerifier.create(client.fetchUserPolicySnapshot())
                .expectErrorMatches(error -> error instanceof IllegalStateException
                        && "projection snapshot watermark changed between pages".equals(error.getMessage()))
                .verify();
    }

    private static MembershipSnapshotClient membershipClient(WebClient webClient) {
        return new MembershipSnapshotClient(
                webClient,
                clientProperties(),
                sessionProperties(),
                jwtProperties()
        );
    }

    private static PolicySnapshotClient policyClient(WebClient webClient) {
        return new PolicySnapshotClient(
                webClient,
                clientProperties(),
                sessionProperties(),
                jwtProperties()
        );
    }

    private static WebClient webClient(String... bodies) {
        AtomicInteger responseIndex = new AtomicInteger();
        ExchangeFunction exchange = request -> {
            int index = responseIndex.getAndIncrement();
            if (index >= bodies.length) {
                return Mono.error(new AssertionError("unexpected snapshot page request"));
            }
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(bodies[index])
                    .build());
        };
        return WebClient.builder().exchangeFunction(exchange).build();
    }

    private static WebClient noContentWebClient() {
        ExchangeFunction exchange = request -> Mono.just(ClientResponse.create(HttpStatus.NO_CONTENT).build());
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
