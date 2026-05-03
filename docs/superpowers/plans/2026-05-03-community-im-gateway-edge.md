# Community IM Gateway Edge Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build `community-im-gateway` as an independent internal IM edge deployable behind the existing unified `community-gateway` entry.

**Architecture:** Add a Spring Boot WebFlux module that owns IM session bootstrap and stable `/ws/im` WebSocket bridging. Keep `community-gateway` as the public entry, `im-realtime` as the internal worker, and `im-core` as IM state owner. The first cut duplicates small edge mechanisms where useful, then retires the old worker-path public contract after the new route is covered.

**Tech Stack:** Java 17, Spring Boot 3.2, Spring WebFlux, Spring Cloud Gateway, Spring Cloud LoadBalancer, Nacos Discovery, Reactor Netty WebSocket, Spring Security OAuth2 Resource Server, Micrometer, Maven, Docker Compose, Vue/Vitest.

---

## Execution Map

Run tasks in order. Tasks 2 and 3 share the new module and should not be parallelized against each other. Task 8 can run in parallel with Task 7 after Task 4 defines the external contract.

- Task 1: Create the module skeleton.
- Task 2: Add edge session and worker selection.
- Task 3: Add stable `/ws/im` edge bridge.
- Task 4: Route public traffic through `community-gateway`.
- Task 5: Retire `im-realtime` public session bootstrap.
- Task 6: Add IM edge observability.
- Task 7: Add deploy topology wiring.
- Task 8: Update frontend contract tests.
- Task 9: Update docs and verification.

---

## File Map

### New production files

- `backend/community-im-gateway/pom.xml`  
  Maven module for the new deployable.
- `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/CommunityImGatewayApplication.java`  
  Spring Boot entry point.
- `backend/community-im-gateway/src/main/resources/application.yml`  
  Runtime config for port, discovery, JWT, CORS, management, public WS URL, worker discovery, and bridge paths.
- `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/security/ImGatewaySecurityConfig.java`  
  WebFlux security chain. Permits `POST /api/im/sessions`, `WS /ws/im`, and health endpoints.
- `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/security/ImGatewayCorsConfig.java`  
  CORS filter for session bootstrap; WebSocket CORS handled by handler mapping.
- `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/security/ImGatewayCorsProperties.java`  
  `im.gateway.cors.allowed-origins`.
- `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/security/JwtVerifier.java`  
  Access-token verifier copied from `im-realtime` with package rename.
- `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/session/ImGatewaySessionProperties.java`  
  Ticket TTL, worker discovery metadata keys, public WS URL override, public WS path.
- `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/session/PublicWsUrlFactory.java`  
  Builds stable absolute `wsUrl` for `OpenImSessionResponse`.
- `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/session/SessionTicketCodec.java`  
  Ticket encoder/decoder compatible with `im-realtime`.
- `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/session/ImSessionService.java`  
  Verifies bearer token, selects worker, signs ticket, returns `OpenImSessionResponse`.
- `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/session/ImSessionApiController.java`  
  `POST /api/im/sessions` WebFlux controller.
- `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/shard/WorkerDescriptor.java`  
  Worker id plus internal WebSocket URI.
- `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/shard/DiscoveredWorkerDescriptorFactory.java`  
  Converts discovery metadata into `WorkerDescriptor`.
- `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/shard/WorkerRegistry.java`  
  Reads current workers from `DiscoveryClient`, filters invalid workers, detects duplicates.
- `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/shard/RendezvousWorkerSelector.java`  
  Selects worker by `CRC32(userId|workerId)`.
- `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/ws/ImGatewayWebSocketConfig.java`  
  Maps external `/ws/im` to the edge handler and configures `ReactorNettyWebSocketClient`.
- `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/ws/ImGatewayFrameCodec.java`  
  Minimal JSON frame codec for `connect` and reject frames.
- `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/ws/ConnectTicketRouter.java`  
  Parses first frame, decodes ticket, resolves target worker.
- `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/ws/InternalWorkerBridge.java`  
  Bridge interface for forwarding external frames to internal workers.
- `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/ws/InternalWorkerBridgeFactory.java`  
  Reactor Netty implementation that forwards trace headers and text frames.
- `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/ws/ExternalImEdgeWebSocketHandler.java`  
  Stable `/ws/im` handler that routes by first `connect(ticket)` frame.
- `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/observability/ImGatewayMetrics.java`  
  Central metric recorder for session, bridge, worker, and ticket failure counters plus active WebSocket gauge.
- `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/im/GatewayImEdgeRouteConfig.java`  
  Adds explicit higher-priority HTTP and WS routes to `community-im-gateway`.
- `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/im/GatewayImEdgeRouteProperties.java`  
  Configures `gateway.im-edge.service-id`, `session-path`, and `ws-path`.

### New test files

- `backend/community-im-gateway/src/test/java/com/nowcoder/community/im/gateway/CommunityImGatewayApplicationTest.java`
- `backend/community-im-gateway/src/test/java/com/nowcoder/community/im/gateway/session/SessionTicketCodecTest.java`
- `backend/community-im-gateway/src/test/java/com/nowcoder/community/im/gateway/session/PublicWsUrlFactoryTest.java`
- `backend/community-im-gateway/src/test/java/com/nowcoder/community/im/gateway/session/ImSessionApiIntegrationTest.java`
- `backend/community-im-gateway/src/test/java/com/nowcoder/community/im/gateway/shard/RendezvousWorkerSelectorTest.java`
- `backend/community-im-gateway/src/test/java/com/nowcoder/community/im/gateway/ws/ImEdgeWebSocketBridgeIntegrationTest.java`
- `backend/community-im-gateway/src/test/java/com/nowcoder/community/im/gateway/observability/ImGatewayMetricsTest.java`
- `backend/community-gateway/src/test/java/com/nowcoder/community/gateway/im/GatewayImEdgeRouteIntegrationTest.java`

### Modified production files

- `backend/pom.xml`  
  Adds `community-im-gateway` module.
- `backend/community-gateway/src/main/resources/application.yml`  
  Adds explicit IM edge route defaults; keeps old worker path as rollback until Task 8.
- `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/security/ImRealtimeSecurityConfig.java`  
  Removes public `POST /api/im/sessions` permit after controller removal.
- `backend/community-im/im-realtime/src/main/resources/application.yml`  
  Documents internal-worker defaults for runtime.
- `deploy/compose.runtime.services.single.yml`  
  Adds `community-im-gateway` service and routes dependencies.
- `deploy/compose.runtime.services.cluster.yml`  
  Adds `community-im-gateway-1..3` services and routes dependencies.
- `frontend/src/im/imRealtimeClient.test.js`  
  Updates expected session `wsUrl` shape to stable `/ws/im`.
- `docs/handbook/architecture.md`
- `docs/handbook/system-design.md`
- `docs/handbook/overview.md`
- `docs/handbook/business-flows.md`
- `docs/handbook/security.md`
- `docs/handbook/operations.md`
- `docs/handbook/local-development.md`
- `docs/handbook/frontend.md`

### Deleted production files

- `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/session/ImSessionApiController.java`
- `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/session/ImSessionService.java`
- `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/session/RendezvousWorkerSelector.java`
- `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/security/JwtVerifier.java`

Keep `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/session/ImSessionProperties.java` and `SessionTicketCodec.java` because the worker still needs its local worker id and ticket decoder.

### Deleted or rewritten test files

- Delete or rewrite `backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/session/ImSessionServiceTest.java`.
- Delete or rewrite `backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/session/ImSessionApiIntegrationTest.java`.
- Keep `backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/session/SessionTicketCodecTest.java`.

---

## Task 1: Create `community-im-gateway` Module Skeleton

**Files:**
- Modify: `backend/pom.xml`
- Create: `backend/community-im-gateway/pom.xml`
- Create: `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/CommunityImGatewayApplication.java`
- Create: `backend/community-im-gateway/src/main/resources/application.yml`
- Create: `backend/community-im-gateway/src/test/java/com/nowcoder/community/im/gateway/CommunityImGatewayApplicationTest.java`

- [ ] **Step 1: Run the module test and confirm the module is not wired**

Run:

```bash
mvn -f backend/pom.xml -pl :community-im-gateway -am -Dtest=CommunityImGatewayApplicationTest test
```

Expected: FAIL with Maven reporting that project `:community-im-gateway` cannot be found.

- [ ] **Step 2: Wire the Maven module**

Modify `backend/pom.xml` and add the new module after `community-gateway`:

```xml
<modules>
    <module>community-common</module>
    <module>community-im</module>
    <module>community-gateway</module>
    <module>community-im-gateway</module>
    <module>community-app</module>
</modules>
```

Create `backend/community-im-gateway/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xmlns="http://maven.apache.org/POM/4.0.0"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.nowcoder.community</groupId>
        <artifactId>community</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>

    <artifactId>community-im-gateway</artifactId>
    <name>community-im-gateway</name>
    <description>Internal IM edge for session bootstrap and WebSocket bridging</description>
    <packaging>jar</packaging>

    <dependencies>
        <dependency>
            <groupId>com.nowcoder.community</groupId>
            <artifactId>community-common-core</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>com.nowcoder.community</groupId>
            <artifactId>community-common-security</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>com.nowcoder.community</groupId>
            <artifactId>community-common-webflux</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>com.nowcoder.community</groupId>
            <artifactId>im-common</artifactId>
            <version>${project.version}</version>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-loadbalancer</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-prometheus</artifactId>
        </dependency>
        <dependency>
            <groupId>net.logstash.logback</groupId>
            <artifactId>logstash-logback-encoder</artifactId>
            <version>7.4</version>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <resources>
            <resource>
                <directory>src/main/resources</directory>
                <filtering>false</filtering>
                <excludes>
                    <exclude>application.yml</exclude>
                </excludes>
            </resource>
            <resource>
                <directory>src/main/resources</directory>
                <filtering>true</filtering>
                <includes>
                    <include>application.yml</include>
                </includes>
            </resource>
        </resources>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-resources-plugin</artifactId>
                <configuration>
                    <delimiters>
                        <delimiter>@</delimiter>
                    </delimiters>
                    <useDefaultDelimiters>false</useDefaultDelimiters>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <mainClass>com.nowcoder.community.im.gateway.CommunityImGatewayApplication</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 3: Add the boot application and baseline config**

Create `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/CommunityImGatewayApplication.java`:

```java
package com.nowcoder.community.im.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CommunityImGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommunityImGatewayApplication.class, args);
    }
}
```

Create `backend/community-im-gateway/src/main/resources/application.yml`:

```yaml
server:
  port: ${SERVER_PORT:18083}

spring:
  application:
    name: community-im-gateway
  cloud:
    nacos:
      discovery:
        server-addr: ${SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR:${NACOS_SERVER_ADDR:localhost:8848}}

community:
  logging:
    service-version: ${SERVICE_VERSION:@project.version@}

security:
  jwt:
    hmac-secret: ${JWT_HMAC_SECRET:}
    issuer: ${JWT_ISSUER:community-auth}

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus

im:
  gateway:
    cors:
      allowed-origins: ${IM_GATEWAY_CORS_ALLOWED_ORIGINS:}
    public-ws-path: ${IM_GATEWAY_PUBLIC_WS_PATH:/ws/im}
    public-ws-url: ${IM_GATEWAY_PUBLIC_WS_URL:}
    session:
      ticket-ttl: ${IM_SESSION_TICKET_TTL:PT2M}
    worker:
      service-id: ${IM_REALTIME_WORKER_SERVICE_ID:im-realtime-worker}
      worker-id-metadata-key: ${IM_SESSION_WORKER_ID_METADATA_KEY:workerId}
      ws-path-metadata-key: ${IM_SESSION_WS_PATH_METADATA_KEY:wsPath}
      ws-port-metadata-key: ${IM_SESSION_WS_PORT_METADATA_KEY:wsPort}
    ws:
      path: ${IM_GATEWAY_WS_PATH:/ws/im}
      first-frame-timeout-ms: ${IM_GATEWAY_FIRST_FRAME_TIMEOUT_MS:5000}
      max-inbound-chars: ${IM_GATEWAY_WS_MAX_INBOUND_CHARS:10000}
```

- [ ] **Step 4: Add the context-load test**

Create `backend/community-im-gateway/src/test/java/com/nowcoder/community/im/gateway/CommunityImGatewayApplicationTest.java`:

```java
package com.nowcoder.community.im.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "security.jwt.hmac-secret=im-gateway-test-jwt-secret-please-change-123456",
        "security.jwt.issuer=community-auth",
        "spring.cloud.nacos.discovery.enabled=false"
})
class CommunityImGatewayApplicationTest {

    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 5: Run the context-load test and confirm it passes**

Run:

```bash
mvn -f backend/pom.xml -pl :community-im-gateway -am -Dtest=CommunityImGatewayApplicationTest test
```

Expected: PASS.

- [ ] **Step 6: Commit Task 1**

```bash
git add backend/pom.xml backend/community-im-gateway
git commit -m "feat: add community IM gateway module"
```

---

## Task 2: Add Edge Session Bootstrap And Worker Selection

**Files:**
- Create: `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/security/JwtVerifier.java`
- Create: `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/session/ImGatewaySessionProperties.java`
- Create: `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/session/PublicWsUrlFactory.java`
- Create: `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/session/SessionTicketCodec.java`
- Create: `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/session/ImSessionService.java`
- Create: `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/session/ImSessionApiController.java`
- Create: `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/shard/WorkerDescriptor.java`
- Create: `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/shard/DiscoveredWorkerDescriptorFactory.java`
- Create: `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/shard/WorkerRegistry.java`
- Create: `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/shard/RendezvousWorkerSelector.java`
- Create: `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/security/ImGatewaySecurityConfig.java`
- Create: `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/security/ImGatewayCorsConfig.java`
- Create: `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/security/ImGatewayCorsProperties.java`
- Create: `backend/community-im-gateway/src/test/java/com/nowcoder/community/im/gateway/session/SessionTicketCodecTest.java`
- Create: `backend/community-im-gateway/src/test/java/com/nowcoder/community/im/gateway/session/PublicWsUrlFactoryTest.java`
- Create: `backend/community-im-gateway/src/test/java/com/nowcoder/community/im/gateway/session/ImSessionApiIntegrationTest.java`
- Create: `backend/community-im-gateway/src/test/java/com/nowcoder/community/im/gateway/shard/RendezvousWorkerSelectorTest.java`

- [ ] **Step 1: Write the failing session and selector tests**

Create `SessionTicketCodecTest` by copying `backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/session/SessionTicketCodecTest.java` and changing the package/imports to `com.nowcoder.community.im.gateway.session`. Keep the same assertions for `sid`, `wid`, `typ`, subject, issue time, and expiry.

Create `PublicWsUrlFactoryTest`:

```java
package com.nowcoder.community.im.gateway.session;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import static org.assertj.core.api.Assertions.assertThat;

class PublicWsUrlFactoryTest {

    @Test
    void shouldUseConfiguredAbsolutePublicWsUrlWhenPresent() {
        ImGatewaySessionProperties properties = new ImGatewaySessionProperties();
        properties.setPublicWsUrl("wss://community.example/ws/im");
        PublicWsUrlFactory factory = new PublicWsUrlFactory(properties);

        String url = factory.build(MockServerHttpRequest.get("http://internal/api/im/sessions").build());

        assertThat(url).isEqualTo("wss://community.example/ws/im");
    }

    @Test
    void shouldDeriveWsUrlFromForwardedHeaders() {
        ImGatewaySessionProperties properties = new ImGatewaySessionProperties();
        properties.setPublicWsPath("/ws/im");
        PublicWsUrlFactory factory = new PublicWsUrlFactory(properties);

        String url = factory.build(MockServerHttpRequest.post("http://community-im-gateway:18083/api/im/sessions")
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Host", "community.example")
                .build());

        assertThat(url).isEqualTo("wss://community.example/ws/im");
    }
}
```

Create `RendezvousWorkerSelectorTest`:

```java
package com.nowcoder.community.im.gateway.shard;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RendezvousWorkerSelectorTest {

    @Test
    void shouldSelectSameWorkerForSameUserAndWorkerSet() {
        RendezvousWorkerSelector selector = new RendezvousWorkerSelector(new WorkerRegistry(List.of(
                new WorkerDescriptor("worker-a", URI.create("ws://127.0.0.1:18081/internal/ws/im")),
                new WorkerDescriptor("worker-b", URI.create("ws://127.0.0.1:18082/internal/ws/im")),
                new WorkerDescriptor("worker-c", URI.create("ws://127.0.0.1:18083/internal/ws/im"))
        )));
        UUID userId = UUID.fromString("00000000-0000-7000-8000-000000000123");

        WorkerDescriptor first = selector.select(userId);
        WorkerDescriptor second = selector.select(userId);

        assertThat(second).isEqualTo(first);
    }
}
```

Create `ImSessionApiIntegrationTest`:

```java
package com.nowcoder.community.im.gateway.session;

import com.nowcoder.community.common.security.jwt.JwtCodecs;
import com.nowcoder.community.common.security.jwt.JwtProperties;
import com.nowcoder.community.im.gateway.CommunityImGatewayApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Instant;

@SpringBootTest(
        classes = CommunityImGatewayApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class ImSessionApiIntegrationTest {

    private static final String SECRET = "im-gateway-session-test-secret-please-change-123456";

    @Autowired
    WebTestClient webTestClient;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.hmac-secret", () -> SECRET);
        registry.add("security.jwt.issuer", () -> "community-auth");
        registry.add("spring.cloud.nacos.discovery.enabled", () -> "false");
        registry.add("im.gateway.public-ws-url", () -> "wss://community.example/ws/im");
        registry.add("spring.cloud.discovery.client.simple.instances.im-realtime-worker[0].uri",
                () -> "http://127.0.0.1:18081");
        registry.add("spring.cloud.discovery.client.simple.instances.im-realtime-worker[0].metadata.workerId",
                () -> "worker-a");
        registry.add("spring.cloud.discovery.client.simple.instances.im-realtime-worker[0].metadata.wsPath",
                () -> "/internal/ws/im");
        registry.add("spring.cloud.discovery.client.simple.instances.im-realtime-worker[0].metadata.wsPort",
                () -> "18081");
    }

    @Test
    void shouldReturnStableWsUrlAndTicket() {
        webTestClient.post()
                .uri("/api/im/sessions")
                .header("Authorization", "Bearer " + accessToken())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.workerId").isEqualTo("worker-a")
                .jsonPath("$.data.wsUrl").isEqualTo("wss://community.example/ws/im")
                .jsonPath("$.data.ticket").isNotEmpty();
    }

    @Test
    void shouldRejectMissingBearerToken() {
        webTestClient.post()
                .uri("/api/im/sessions")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    private static String accessToken() {
        JwtProperties properties = new JwtProperties();
        properties.setHmacSecret(SECRET);
        properties.setIssuer("community-auth");
        JwtEncoder encoder = JwtCodecs.jwtEncoder(properties);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("community-auth")
                .subject("00000000-0000-7000-8000-000000000123")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
```

- [ ] **Step 2: Run the focused tests and confirm they fail**

Run:

```bash
mvn -f backend/pom.xml -pl :community-im-gateway -am -Dtest=SessionTicketCodecTest,PublicWsUrlFactoryTest,RendezvousWorkerSelectorTest,ImSessionApiIntegrationTest test
```

Expected: FAIL because the session, shard, and security classes do not exist.

- [ ] **Step 3: Implement security, session, and shard classes**

Implement `JwtVerifier` by copying the current implementation from `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/security/JwtVerifier.java` and changing the package to `com.nowcoder.community.im.gateway.security`.

Implement `SessionTicketCodec` by copying the current implementation from `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/session/SessionTicketCodec.java` and changing the package to `com.nowcoder.community.im.gateway.session`. Keep claim names `sid`, `wid`, `typ` and token type `im-session-ticket` unchanged.

Implement `ImGatewaySessionProperties`:

```java
package com.nowcoder.community.im.gateway.session;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "im.gateway")
public class ImGatewaySessionProperties {

    private String publicWsPath = "/ws/im";
    private String publicWsUrl = "";
    private final Session session = new Session();
    private final Worker worker = new Worker();

    public String getPublicWsPath() {
        return publicWsPath;
    }

    public void setPublicWsPath(String publicWsPath) {
        this.publicWsPath = publicWsPath;
    }

    public String getPublicWsUrl() {
        return publicWsUrl;
    }

    public void setPublicWsUrl(String publicWsUrl) {
        this.publicWsUrl = publicWsUrl;
    }

    public Session getSession() {
        return session;
    }

    public Worker getWorker() {
        return worker;
    }

    public static class Session {
        private Duration ticketTtl = Duration.ofMinutes(2);

        public Duration getTicketTtl() {
            return ticketTtl;
        }

        public void setTicketTtl(Duration ticketTtl) {
            this.ticketTtl = ticketTtl == null || ticketTtl.isZero() || ticketTtl.isNegative()
                    ? Duration.ofMinutes(2)
                    : ticketTtl;
        }
    }

    public static class Worker {
        private String serviceId = "im-realtime-worker";
        private String workerIdMetadataKey = "workerId";
        private String wsPathMetadataKey = "wsPath";
        private String wsPortMetadataKey = "wsPort";

        public String getServiceId() {
            return serviceId;
        }

        public void setServiceId(String serviceId) {
            this.serviceId = serviceId;
        }

        public String getWorkerIdMetadataKey() {
            return workerIdMetadataKey;
        }

        public void setWorkerIdMetadataKey(String workerIdMetadataKey) {
            this.workerIdMetadataKey = workerIdMetadataKey;
        }

        public String getWsPathMetadataKey() {
            return wsPathMetadataKey;
        }

        public void setWsPathMetadataKey(String wsPathMetadataKey) {
            this.wsPathMetadataKey = wsPathMetadataKey;
        }

        public String getWsPortMetadataKey() {
            return wsPortMetadataKey;
        }

        public void setWsPortMetadataKey(String wsPortMetadataKey) {
            this.wsPortMetadataKey = wsPortMetadataKey;
        }
    }
}
```

Implement `WorkerDescriptor`, `DiscoveredWorkerDescriptorFactory`, and `WorkerRegistry` by copying the current gateway classes from `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/shard/` and changing packages to `com.nowcoder.community.im.gateway.shard`. Replace `WorkerDiscoveryProperties` usage with `ImGatewaySessionProperties`.

Implement `RendezvousWorkerSelector` with the same score algorithm from current `im-realtime`:

```java
private static long score(UUID userId, String workerId) {
    CRC32 crc32 = new CRC32();
    byte[] bytes = (String.valueOf(userId) + "|" + workerId).getBytes(StandardCharsets.UTF_8);
    crc32.update(bytes, 0, bytes.length);
    return crc32.getValue();
}
```

Implement `PublicWsUrlFactory` using `im.gateway.public-ws-url` override first, then `X-Forwarded-Proto` and `X-Forwarded-Host`, then request URI scheme and host. Convert `http` to `ws` and `https` to `wss`.

Implement `ImSessionService` with this behavior:

```java
public OpenImSessionResponse openSession(String authorizationHeader, ServerHttpRequest request) {
    String accessToken = extractBearerToken(authorizationHeader);
    JwtVerifier.VerifiedJwt verified = jwtVerifier.verify(accessToken);
    WorkerDescriptor worker = workerSelector.select(verified.userId());
    String sessionId = UUID.randomUUID().toString();
    Instant expiresAt = Instant.now().plus(properties.getSession().getTicketTtl());
    String ticket = sessionTicketCodec.encode(sessionId, verified.userId(), worker.getId(), expiresAt);
    return new OpenImSessionResponse(
            sessionId,
            worker.getId(),
            publicWsUrlFactory.build(request),
            ticket,
            expiresAt.toEpochMilli()
    );
}
```

Implement `ImSessionApiController`:

```java
@RestController
@RequestMapping("/api/im/sessions")
public class ImSessionApiController {

    private final ImSessionService imSessionService;

    public ImSessionApiController(ImSessionService imSessionService) {
        this.imSessionService = imSessionService;
    }

    @PostMapping
    public Result<OpenImSessionResponse> openSession(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            ServerHttpRequest request
    ) {
        return Result.ok(imSessionService.openSession(authorizationHeader, request));
    }
}
```

Implement `ImGatewaySecurityConfig` to permit `/api/im/sessions`, `/ws/im`, `/actuator/health`, `/actuator/info`, and `/actuator/prometheus`, and deny all other paths.

Implement `ImGatewayCorsConfig` by following `GatewayCorsConfig`, registering CORS for `/api/**`, skipping WebSocket upgrade requests, and exposing `X-Trace-Id`.

- [ ] **Step 4: Re-run session and selector tests**

Run:

```bash
mvn -f backend/pom.xml -pl :community-im-gateway -am -Dtest=SessionTicketCodecTest,PublicWsUrlFactoryTest,RendezvousWorkerSelectorTest,ImSessionApiIntegrationTest test
```

Expected: PASS.

- [ ] **Step 5: Commit Task 2**

```bash
git add backend/community-im-gateway
git commit -m "feat: add IM edge session bootstrap"
```

---

## Task 3: Add Stable `/ws/im` WebSocket Edge Bridge

**Files:**
- Create: `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/ws/ImGatewayWebSocketConfig.java`
- Create: `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/ws/ImGatewayFrameCodec.java`
- Create: `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/ws/ConnectTicketRouter.java`
- Create: `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/ws/InternalWorkerBridge.java`
- Create: `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/ws/InternalWorkerBridgeFactory.java`
- Create: `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/ws/ExternalImEdgeWebSocketHandler.java`
- Create: `backend/community-im-gateway/src/test/java/com/nowcoder/community/im/gateway/ws/ImEdgeWebSocketBridgeIntegrationTest.java`

- [ ] **Step 1: Write the failing bridge integration test**

Create `ImEdgeWebSocketBridgeIntegrationTest`:

```java
package com.nowcoder.community.im.gateway.ws;

import com.nowcoder.community.common.security.jwt.JwtCodecs;
import com.nowcoder.community.common.security.jwt.JwtProperties;
import com.nowcoder.community.im.gateway.CommunityImGatewayApplication;
import com.nowcoder.community.im.gateway.session.SessionTicketCodec;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = CommunityImGatewayApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class ImEdgeWebSocketBridgeIntegrationTest {

    private static final String SECRET = "im-gateway-ws-test-secret-please-change-123456";
    private static volatile DisposableServer workerServer;

    @Autowired
    @org.springframework.boot.test.web.server.LocalServerPort
    int port;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.hmac-secret", () -> SECRET);
        registry.add("security.jwt.issuer", () -> "community-auth");
        registry.add("spring.cloud.nacos.discovery.enabled", () -> "false");
        registry.add("spring.cloud.discovery.client.simple.instances.im-realtime-worker[0].uri",
                () -> "http://127.0.0.1:" + workerPort());
        registry.add("spring.cloud.discovery.client.simple.instances.im-realtime-worker[0].metadata.workerId",
                () -> "worker-a");
        registry.add("spring.cloud.discovery.client.simple.instances.im-realtime-worker[0].metadata.wsPath",
                () -> "/internal/ws/im");
        registry.add("spring.cloud.discovery.client.simple.instances.im-realtime-worker[0].metadata.wsPort",
                () -> String.valueOf(workerPort()));
    }

    @AfterAll
    static void stopWorker() {
        if (workerServer != null) {
            workerServer.disposeNow();
            workerServer = null;
        }
    }

    @Test
    void shouldBridgeStableWsPathToWorkerFromConnectTicket() throws Exception {
        LinkedBlockingQueue<String> received = new LinkedBlockingQueue<>();
        Sinks.Many<String> outbound = Sinks.many().unicast().onBackpressureBuffer();
        ReactorNettyWebSocketClient client = new ReactorNettyWebSocketClient();
        URI uri = URI.create("ws://127.0.0.1:" + port + "/ws/im");

        Disposable handle = client.execute(uri, session -> {
                    Mono<Void> send = session.send(outbound.asFlux().map(session::textMessage));
                    Mono<Void> recv = session.receive()
                            .map(WebSocketMessage::getPayloadAsText)
                            .doOnNext(received::offer)
                            .take(2)
                            .then();
                    return Mono.when(send, recv);
                })
                .subscribe();
        try {
            String ticket = ticket("worker-a");
            outbound.tryEmitNext("{\"type\":\"connect\",\"ticket\":\"" + ticket + "\"}");
            outbound.tryEmitNext("{\"type\":\"ping\",\"ts\":1}");
            outbound.tryEmitComplete();

            assertThat(received.poll(5, TimeUnit.SECONDS)).isEqualTo("worker-a:{\"type\":\"connect\",\"ticket\":\"" + ticket + "\"}");
            assertThat(received.poll(5, TimeUnit.SECONDS)).isEqualTo("worker-a:{\"type\":\"ping\",\"ts\":1}");
        } finally {
            handle.dispose();
        }
    }

    @Test
    void shouldRejectNonConnectFirstFrame() throws Exception {
        LinkedBlockingQueue<String> received = new LinkedBlockingQueue<>();
        Sinks.Many<String> outbound = Sinks.many().unicast().onBackpressureBuffer();
        ReactorNettyWebSocketClient client = new ReactorNettyWebSocketClient();

        Disposable handle = client.execute(URI.create("ws://127.0.0.1:" + port + "/ws/im"), session -> {
                    Mono<Void> send = session.send(outbound.asFlux().map(session::textMessage));
                    Mono<Void> recv = session.receive()
                            .map(WebSocketMessage::getPayloadAsText)
                            .doOnNext(received::offer)
                            .take(1)
                            .then();
                    return Mono.when(send, recv);
                })
                .subscribe();
        try {
            outbound.tryEmitNext("{\"type\":\"ping\"}");
            outbound.tryEmitComplete();
            assertThat(received.poll(5, TimeUnit.SECONDS)).contains("\"reasonCode\":\"connect_required\"");
        } finally {
            handle.dispose();
        }
    }

    private static synchronized int workerPort() {
        if (workerServer == null) {
            workerServer = HttpServer.create()
                    .host("127.0.0.1")
                    .port(0)
                    .route(routes -> routes.ws("/internal/ws/im", (in, out) ->
                            out.sendString(in.receive().asString().map(text -> "worker-a:" + text))
                    ))
                    .bindNow(Duration.ofSeconds(5));
        }
        return workerServer.port();
    }

    private static String ticket(String workerId) {
        JwtProperties properties = new JwtProperties();
        properties.setHmacSecret(SECRET);
        properties.setIssuer("community-auth");
        SessionTicketCodec codec = new SessionTicketCodec(properties, JwtCodecs.jwtDecoder(properties));
        return codec.encode(
                "sess-1",
                UUID.fromString("00000000-0000-7000-8000-000000000123"),
                workerId,
                Instant.now().plusSeconds(120)
        );
    }
}
```

- [ ] **Step 2: Run the bridge test and confirm it fails**

Run:

```bash
mvn -f backend/pom.xml -pl :community-im-gateway -am -Dtest=ImEdgeWebSocketBridgeIntegrationTest test
```

Expected: FAIL because the WebSocket handler and bridge do not exist.

- [ ] **Step 3: Implement frame codec, router, bridge, and handler**

Implement `ImGatewayFrameCodec` with `ObjectMapper.readTree`, `ObjectMapper.treeToValue`, and `ObjectMapper.writeValueAsString`, matching the current `im-realtime` `ImFrameCodec`.

Implement `InternalWorkerBridge`:

```java
package com.nowcoder.community.im.gateway.ws;

import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface InternalWorkerBridge {

    Mono<Void> bridge(WebSocketSession externalSession, Flux<String> outboundFrames);
}
```

Implement `InternalWorkerBridgeFactory` by copying the current bridge code from `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/ws/InternalWorkerBridgeFactory.java`, changing the package, bean qualifier to `"imGatewayWebSocketClient"`, and class names.

Implement `ConnectTicketRouter`:

```java
public RoutingDecision route(String firstFrame) {
    JsonNode node = frameCodec.readTree(firstFrame);
    if (!"connect".equals(node.path("type").asText(""))) {
        throw new RoutingException(401, "connect_required", "connect required");
    }
    ConnectFrame frame = frameCodec.read(node, ConnectFrame.class);
    SessionTicketCodec.TicketClaims claims = sessionTicketCodec.decode(frame.ticket());
    WorkerDescriptor worker = workerRegistry.find(claims.workerId())
            .orElseThrow(() -> new RoutingException(503, "worker_unavailable", "worker unavailable"));
    return new RoutingDecision(claims.sessionId(), claims.workerId(), worker.getUri());
}
```

Implement `ExternalImEdgeWebSocketHandler` using `switchOnFirst`:

```java
@Override
public Mono<Void> handle(WebSocketSession session) {
    Flux<String> inbound = session.receive().map(WebSocketMessage::getPayloadAsText);
    return inbound.switchOnFirst((signal, frames) -> {
        if (!signal.hasValue()) {
            return rejectAndClose(session, "connect_required", "connect required");
        }
        String firstFrame = signal.get();
        ConnectTicketRouter.RoutingDecision decision;
        try {
            decision = connectTicketRouter.route(firstFrame);
        } catch (ConnectTicketRouter.RoutingException e) {
            return rejectAndClose(session, e.reasonCode(), e.getMessage());
        } catch (RuntimeException e) {
            return rejectAndClose(session, "invalid_ticket", "invalid ticket");
        }
        Flux<String> outbound = Flux.concat(Mono.just(firstFrame), frames.skip(1));
        return bridgeFactory.create(decision.workerUri()).bridge(session, outbound);
    });
}
```

Use `RejectFrame("reject", "connect", "", "", 401, reasonCode, message)` for reject frames.

Implement `ImGatewayWebSocketConfig`:

```java
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ImGatewaySessionProperties.class)
public class ImGatewayWebSocketConfig {

    @Bean
    HandlerMapping imGatewayWebSocketMapping(
            ExternalImEdgeWebSocketHandler handler,
            ImGatewaySessionProperties properties
    ) {
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setOrder(-1);
        mapping.setUrlMap(Map.of(properties.getPublicWsPath(), handler));
        return mapping;
    }

    @Bean
    WebSocketHandlerAdapter webSocketHandlerAdapter() {
        return new WebSocketHandlerAdapter();
    }

    @Bean
    @Primary
    ReactorNettyWebSocketClient imGatewayWebSocketClient() {
        return new ReactorNettyWebSocketClient();
    }
}
```

- [ ] **Step 4: Run bridge tests**

Run:

```bash
mvn -f backend/pom.xml -pl :community-im-gateway -am -Dtest=ImEdgeWebSocketBridgeIntegrationTest test
```

Expected: PASS.

- [ ] **Step 5: Commit Task 3**

```bash
git add backend/community-im-gateway
git commit -m "feat: add stable IM websocket edge bridge"
```

---

## Task 4: Route IM Edge Traffic Through `community-gateway`

**Files:**
- Create: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/im/GatewayImEdgeRouteConfig.java`
- Create: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/im/GatewayImEdgeRouteProperties.java`
- Create: `backend/community-gateway/src/test/java/com/nowcoder/community/gateway/im/GatewayImEdgeRouteIntegrationTest.java`
- Modify: `backend/community-gateway/src/main/resources/application.yml`
- Modify: `backend/community-gateway/src/test/java/com/nowcoder/community/gateway/http/HttpRoutingIntegrationTest.java`

- [ ] **Step 1: Write failing route integration tests**

Create `GatewayImEdgeRouteIntegrationTest` with two stub upstreams: `community-im-gateway` and `im-core`. Verify:

- `POST /api/im/sessions` reaches `community-im-gateway`.
- `GET /api/im/conversations` still reaches `im-core`.
- `WS /ws/im` reaches `community-im-gateway`.

Use the existing `HttpRoutingIntegrationTest` and `WsTransparentProxyIntegrationTest` as templates. For WebSocket, register a simple service instance:

```java
registry.add("spring.cloud.discovery.client.simple.instances.community-im-gateway[0].uri",
        () -> "http://127.0.0.1:" + imGatewayPort());
registry.add("gateway.im-edge.service-id", () -> "community-im-gateway");
registry.add("gateway.im-edge.session-path", () -> "/api/im/sessions");
registry.add("gateway.im-edge.ws-path", () -> "/ws/im");
```

The stub IM gateway server should expose:

```java
.route(routes -> routes
    .post("/api/im/sessions", (req, res) -> res.sendString(Mono.just("{\"upstream\":\"im-edge\"}")))
    .ws("/ws/im", (in, out) -> out.sendString(in.receive().asString().map(text -> "im-edge:" + text)))
)
```

- [ ] **Step 2: Run route tests and confirm failure**

Run:

```bash
mvn -f backend/pom.xml -pl :community-gateway -am -Dtest=GatewayImEdgeRouteIntegrationTest,HttpRoutingIntegrationTest test
```

Expected: FAIL because no explicit IM edge route exists and `/api/im/sessions` still follows `/api/im/**`.

- [ ] **Step 3: Implement route properties and route locator**

Create `GatewayImEdgeRouteProperties`:

```java
package com.nowcoder.community.gateway.im;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.im-edge")
public class GatewayImEdgeRouteProperties {

    private String serviceId = "community-im-gateway";
    private String sessionPath = "/api/im/sessions";
    private String wsPath = "/ws/im";

    public String getServiceId() {
        return serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    public String getSessionPath() {
        return sessionPath;
    }

    public void setSessionPath(String sessionPath) {
        this.sessionPath = sessionPath;
    }

    public String getWsPath() {
        return wsPath;
    }

    public void setWsPath(String wsPath) {
        this.wsPath = wsPath;
    }
}
```

Create `GatewayImEdgeRouteConfig` with a separate `RouteLocator`:

```java
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayImEdgeRouteProperties.class)
public class GatewayImEdgeRouteConfig {

    @Bean
    RouteLocator gatewayImEdgeRoutes(RouteLocatorBuilder builder, GatewayImEdgeRouteProperties properties) {
        String serviceId = properties.getServiceId().trim();
        return builder.routes()
                .route("im-session-edge", route -> route
                        .order(-100)
                        .path(properties.getSessionPath())
                        .uri("lb://" + serviceId))
                .route("im-ws-edge", route -> route
                        .order(-100)
                        .path(properties.getWsPath())
                        .uri("lb:ws://" + serviceId))
                .build();
    }
}
```

Modify `backend/community-gateway/src/main/resources/application.yml`:

```yaml
gateway:
  im-edge:
    service-id: ${GATEWAY_IM_EDGE_SERVICE_ID:community-im-gateway}
    session-path: /api/im/sessions
    ws-path: /ws/im
```

Keep the existing `gateway.ws.proxy.path: /ws/im/workers/**` during this task for rollback compatibility.

- [ ] **Step 4: Re-run route tests**

Run:

```bash
mvn -f backend/pom.xml -pl :community-gateway -am -Dtest=GatewayImEdgeRouteIntegrationTest,HttpRoutingIntegrationTest test
```

Expected: PASS.

- [ ] **Step 5: Commit Task 4**

```bash
git add backend/community-gateway
git commit -m "feat: route IM edge traffic through gateway"
```

---

## Task 5: Retire `im-realtime` Public Session Bootstrap

**Files:**
- Delete: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/session/ImSessionApiController.java`
- Delete: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/session/ImSessionService.java`
- Delete: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/session/RendezvousWorkerSelector.java`
- Delete: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/security/JwtVerifier.java`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/security/ImRealtimeSecurityConfig.java`
- Delete or rewrite: `backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/session/ImSessionServiceTest.java`
- Delete or rewrite: `backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/session/ImSessionApiIntegrationTest.java`
- Modify: `backend/community-im/im-realtime/src/main/resources/application.yml`

- [ ] **Step 1: Run existing im-realtime tests to capture current behavior**

Run:

```bash
mvn -f backend/pom.xml -pl :im-realtime -am -Dtest=ImSessionServiceTest,ImSessionApiIntegrationTest,SessionTicketCodecTest,ImRealtimeWebSocketIntegrationTest test
```

Expected: `ImSessionServiceTest` and `ImSessionApiIntegrationTest` PASS before deletion; this confirms the behavior being moved is currently covered.

- [ ] **Step 2: Delete moved endpoint and selector classes**

Remove the four production files listed in this task. Keep `ImSessionProperties` and `SessionTicketCodec`.

Modify `ImRealtimeSecurityConfig` by removing:

```java
.pathMatchers("/api/im/sessions").permitAll()
```

The remaining permits should be actuator paths and the configured WebSocket path.

- [ ] **Step 3: Delete moved im-realtime session tests**

Delete:

```text
backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/session/ImSessionServiceTest.java
backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/session/ImSessionApiIntegrationTest.java
```

Keep `SessionTicketCodecTest`.

- [ ] **Step 4: Update im-realtime config comments**

In `backend/community-im/im-realtime/src/main/resources/application.yml`, update comments around `im.edge` and `im.ws.path` to say runtime compose uses internal worker mode and session bootstrap is owned by `community-im-gateway`.

Do not remove `im.session.worker-id` or ticket TTL configuration because `ImWebSocketHandler` still uses `ImSessionProperties` and `SessionTicketCodec`.

- [ ] **Step 5: Run im-realtime focused tests**

Run:

```bash
mvn -f backend/pom.xml -pl :im-realtime -am -Dtest=SessionTicketCodecTest,ImRealtimeWebSocketIntegrationTest test
```

Expected: PASS.

- [ ] **Step 6: Commit Task 5**

```bash
git add -A backend/community-im/im-realtime
git commit -m "refactor: move IM session bootstrap out of realtime"
```

---

## Task 6: Add IM Edge Observability

**Files:**
- Create: `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/observability/ImGatewayMetrics.java`
- Create: `backend/community-im-gateway/src/test/java/com/nowcoder/community/im/gateway/observability/ImGatewayMetricsTest.java`
- Modify: `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/session/ImSessionService.java`
- Modify: `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/ws/ExternalImEdgeWebSocketHandler.java`
- Modify: `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/ws/ConnectTicketRouter.java`

- [ ] **Step 1: Write failing metrics tests**

Create `ImGatewayMetricsTest`:

```java
package com.nowcoder.community.im.gateway.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImGatewayMetricsTest {

    @Test
    void shouldRecordSessionAndBridgeMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ImGatewayMetrics metrics = new ImGatewayMetrics(registry);

        metrics.sessionOpened();
        metrics.sessionFailed("invalid_token");
        metrics.bridgeOpened();
        metrics.bridgeFailed("worker_unavailable");
        metrics.invalidFirstFrame();
        metrics.invalidTicket();
        metrics.workerUnavailable();
        metrics.connectionOpened();
        metrics.connectionClosed();

        assertThat(registry.counter("community.im.gateway.session.opened").count()).isEqualTo(1.0);
        assertThat(registry.counter("community.im.gateway.session.failed", "reason", "invalid_token").count()).isEqualTo(1.0);
        assertThat(registry.counter("community.im.gateway.bridge.opened").count()).isEqualTo(1.0);
        assertThat(registry.counter("community.im.gateway.bridge.failed", "reason", "worker_unavailable").count()).isEqualTo(1.0);
        assertThat(registry.counter("community.im.gateway.ws.invalid_first_frame").count()).isEqualTo(1.0);
        assertThat(registry.counter("community.im.gateway.ticket.invalid").count()).isEqualTo(1.0);
        assertThat(registry.counter("community.im.gateway.worker.unavailable").count()).isEqualTo(1.0);
        assertThat(registry.get("community.im.gateway.ws.active").gauge().value()).isEqualTo(0.0);
    }
}
```

- [ ] **Step 2: Run metrics test and confirm failure**

Run:

```bash
mvn -f backend/pom.xml -pl :community-im-gateway -am -Dtest=ImGatewayMetricsTest test
```

Expected: FAIL because `ImGatewayMetrics` does not exist.

- [ ] **Step 3: Implement metric recorder**

Create `ImGatewayMetrics`:

```java
package com.nowcoder.community.im.gateway.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ImGatewayMetrics {

    private final MeterRegistry registry;
    private final AtomicInteger activeConnections = new AtomicInteger();

    public ImGatewayMetrics(MeterRegistry registry) {
        this.registry = registry;
        registry.gauge("community.im.gateway.ws.active", activeConnections);
    }

    public void sessionOpened() {
        registry.counter("community.im.gateway.session.opened").increment();
    }

    public void sessionFailed(String reason) {
        registry.counter("community.im.gateway.session.failed", "reason", sanitize(reason)).increment();
    }

    public void bridgeOpened() {
        registry.counter("community.im.gateway.bridge.opened").increment();
    }

    public void bridgeFailed(String reason) {
        registry.counter("community.im.gateway.bridge.failed", "reason", sanitize(reason)).increment();
    }

    public void invalidFirstFrame() {
        registry.counter("community.im.gateway.ws.invalid_first_frame").increment();
    }

    public void invalidTicket() {
        registry.counter("community.im.gateway.ticket.invalid").increment();
    }

    public void workerUnavailable() {
        registry.counter("community.im.gateway.worker.unavailable").increment();
    }

    public void connectionOpened() {
        activeConnections.incrementAndGet();
    }

    public void connectionClosed() {
        activeConnections.updateAndGet(value -> Math.max(0, value - 1));
    }

    private static String sanitize(String reason) {
        if (reason == null || reason.isBlank()) {
            return "unknown";
        }
        return reason.trim().replaceAll("[^a-zA-Z0-9_.-]", "_");
    }
}
```

- [ ] **Step 4: Wire metrics into session and WebSocket paths**

In `ImSessionService.openSession`, call:

```java
metrics.sessionOpened();
```

after ticket creation succeeds. In `catch` blocks or before throwing known `ResponseStatusException`, call:

```java
metrics.sessionFailed("invalid_token");
metrics.sessionFailed("no_workers");
metrics.sessionFailed("unexpected");
```

Use `invalid_token` for missing or invalid bearer tokens. Use `no_workers` when worker selection fails because discovery has no valid workers. Use `unexpected` for any other runtime failure before the session response is returned.

In `ExternalImEdgeWebSocketHandler.handle`, call:

```java
metrics.connectionOpened();
```

when handling begins and:

```java
.doFinally(signalType -> metrics.connectionClosed())
```

on the returned `Mono`.

When first-frame routing fails, call `invalidFirstFrame`, `invalidTicket`, `workerUnavailable`, or `bridgeFailed(reason)` according to the reason. When the bridge starts successfully, call `bridgeOpened`.

- [ ] **Step 5: Run metrics and existing IM gateway tests**

Run:

```bash
mvn -f backend/pom.xml -pl :community-im-gateway -am -Dtest=ImGatewayMetricsTest,ImSessionApiIntegrationTest,ImEdgeWebSocketBridgeIntegrationTest test
```

Expected: PASS.

- [ ] **Step 6: Commit Task 6**

```bash
git add backend/community-im-gateway
git commit -m "feat: add IM gateway metrics"
```

---

## Task 7: Add Deploy Topology Wiring

**Files:**
- Modify: `deploy/compose.runtime.services.single.yml`
- Modify: `deploy/compose.runtime.services.cluster.yml`
- Modify: `deploy/README.md`
- Modify: `docs/handbook/local-development.md`

- [ ] **Step 1: Add single-topology service**

In `deploy/compose.runtime.services.single.yml`, add base anchor after `x-community-gateway-base`:

```yaml
x-community-im-gateway-base: &community-im-gateway-base
  build:
    context: ../backend
    dockerfile: ../deploy/Dockerfile.backend-service
    args:
      MODULE: community-im-gateway
      OTEL_JAVA_AGENT_VERSION: ${OTEL_JAVA_AGENT_VERSION:-2.23.0}
  mem_limit: ${COMMUNITY_IM_GATEWAY_MEM_LIMIT:-512m}
  volumes:
  - observability_logs:/var/log/community
```

Add service after `community-gateway` or before `im-core`:

```yaml
  community-im-gateway:
    <<: *community-im-gateway-base
    environment:
    - SERVER_PORT=18083
    - OTEL_ENABLED=${OTEL_ENABLED:-false}
    - OTEL_EXPORTER_OTLP_ENDPOINT=${OTEL_EXPORTER_OTLP_ENDPOINT:-http://observability-gateway-edot-collector:4318}
    - OTEL_EXPORTER_OTLP_PROTOCOL=${OTEL_EXPORTER_OTLP_PROTOCOL:-http/protobuf}
    - OTEL_SERVICE_NAME=community-im-gateway
    - OTEL_RESOURCE_ATTRIBUTES=deployment.environment=local-compose
    - SERVICE_VERSION=${SERVICE_VERSION:-local-compose}
    - JAVA_OPTS=${COMMUNITY_IM_GATEWAY_JAVA_OPTS:--XX:+UseG1GC -XX:+UseStringDeduplication -XX:InitialRAMPercentage=20.0 -XX:MaxRAMPercentage=55.0 -XX:MaxMetaspaceSize=192m -XX:+ExitOnOutOfMemoryError}
    - SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-dev},volume-log-export
    - COMMUNITY_LOGGING_DIR=/var/log/community
    - COMMUNITY_LOGGING_FILE_NAME=community-im-gateway.json.log
    - JWT_HMAC_SECRET=${JWT_HMAC_SECRET}
    - NACOS_SERVER_ADDR=${NACOS_SERVER_ADDR:-nacos:8848}
    - SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR=${NACOS_SERVER_ADDR:-nacos:8848}
    - IM_GATEWAY_CORS_ALLOWED_ORIGINS=${BROWSER_ALLOWED_ORIGINS:?BROWSER_ALLOWED_ORIGINS is required}
    - IM_GATEWAY_PUBLIC_WS_URL=${IM_GATEWAY_PUBLIC_WS_URL:-ws://localhost:12880/ws/im}
    - IM_REALTIME_WORKER_SERVICE_ID=${IM_REALTIME_WORKER_SERVICE_ID:-im-realtime-worker}
    depends_on:
      nacos-db-bootstrap:
        condition: service_completed_successfully
      nacos:
        condition: service_started
      im-realtime:
        condition: service_started
    networks:
      default:
        aliases:
        - community-im-gateway
```

Add `community-im-gateway` to `community-gateway.depends_on` and set:

```yaml
    - GATEWAY_IM_EDGE_SERVICE_ID=community-im-gateway
```

- [ ] **Step 2: Add cluster-topology services**

In `deploy/compose.runtime.services.cluster.yml`, add `x-community-im-gateway-base` matching the single topology.

Add `community-im-gateway-1`, `community-im-gateway-2`, and `community-im-gateway-3` with:

```yaml
    - SERVER_PORT=18083
    - OTEL_SERVICE_NAME=community-im-gateway
    - COMMUNITY_LOGGING_FILE_NAME=community-im-gateway-1.json.log
    - JWT_HMAC_SECRET=${JWT_HMAC_SECRET}
    - NACOS_SERVER_ADDR=${NACOS_SERVER_ADDR:-nacos-1:8848,nacos-2:8848,nacos-3:8848}
    - SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR=${NACOS_SERVER_ADDR:-nacos-1:8848,nacos-2:8848,nacos-3:8848}
    - IM_GATEWAY_CORS_ALLOWED_ORIGINS=${BROWSER_ALLOWED_ORIGINS:?BROWSER_ALLOWED_ORIGINS is required}
    - IM_GATEWAY_PUBLIC_WS_URL=${IM_GATEWAY_PUBLIC_WS_URL:-ws://localhost:12880/ws/im}
    - IM_REALTIME_WORKER_SERVICE_ID=${IM_REALTIME_WORKER_SERVICE_ID:-im-realtime-worker}
```

Use distinct log file names for `-2` and `-3`. Put all three on the default network alias `community-im-gateway` so discovery and Docker DNS can see the service name. Add all three as dependencies of `community-gateway-1..3`.

- [ ] **Step 3: Ensure im-realtime stays internal-worker**

Verify the existing `im-realtime` services keep:

```yaml
- IM_EDGE_MODE=internal-worker
- IM_WS_PATH=${IM_INTERNAL_WORKER_WS_PATH:-/internal/ws/im}
```

Do not expose `im-realtime` directly through NGINX.

- [ ] **Step 4: Validate compose config**

Run:

```bash
docker compose -f deploy/compose.yml -f deploy/compose.runtime.services.single.yml config >/tmp/community-compose-single.yml
docker compose -f deploy/compose.yml -f deploy/compose.runtime.services.cluster.yml config >/tmp/community-compose-cluster.yml
```

Expected: both commands exit 0.

- [ ] **Step 5: Commit Task 7**

```bash
git add deploy/compose.runtime.services.single.yml deploy/compose.runtime.services.cluster.yml deploy/README.md docs/handbook/local-development.md
git commit -m "deploy: add community IM gateway topology"
```

---

## Task 8: Update Frontend Contract Tests

**Files:**
- Modify: `frontend/src/im/imRealtimeClient.test.js`
- Optional modify: `frontend/README.md`

- [ ] **Step 1: Update tests to use stable `wsUrl`**

Change mock session responses from:

```js
wsUrl: 'wss://edge.example.com/ws/im/workers/worker-a'
```

to:

```js
wsUrl: 'wss://edge.example.com/ws/im'
```

In the reconnect test, use the same stable URL for all workers:

```js
wsUrl: 'wss://edge.example.com/ws/im'
```

Update expectations:

```js
expect(FakeWebSocket.instances[0].url).toBe('wss://edge.example.com/ws/im')
expect(FakeWebSocket.instances[1].url).toBe('wss://edge.example.com/ws/im')
expect(FakeWebSocket.instances[2].url).toBe('wss://edge.example.com/ws/im')
```

- [ ] **Step 2: Run frontend IM tests**

Run:

```bash
npm test -- src/im/imRealtimeClient.test.js
```

from `frontend/`.

Expected: PASS.

- [ ] **Step 3: Commit Task 8**

```bash
git add frontend/src/im/imRealtimeClient.test.js frontend/README.md
git commit -m "test: expect stable IM websocket URL"
```

---

## Task 9: Update Docs, Retire Primary Worker Path, And Verify

**Files:**
- Modify: `backend/community-gateway/src/main/resources/application.yml`
- Modify: `docs/handbook/architecture.md`
- Modify: `docs/handbook/system-design.md`
- Modify: `docs/handbook/overview.md`
- Modify: `docs/handbook/business-flows.md`
- Modify: `docs/handbook/security.md`
- Modify: `docs/handbook/operations.md`
- Modify: `docs/handbook/local-development.md`
- Modify: `docs/handbook/frontend.md`
- Modify: `docs/handbook/testing.md`
- Modify: `docs/handbook/integration-contracts.md`

- [ ] **Step 1: Update handbook references**

Replace primary-contract references to:

```text
POST /api/im/sessions -> WS /ws/im
```

Replace text that says `/api/im/sessions` returns `/ws/im/workers/{workerId}` with:

```text
`/api/im/sessions` returns a stable `wsUrl` at `/ws/im`; the session ticket remains bound to a selected internal `im-realtime` worker.
```

Document deployable roles:

```text
community-gateway: unified public entry
community-im-gateway: internal IM edge for session bootstrap and stable WebSocket bridge
im-realtime: internal WebSocket worker at /internal/ws/im
im-core: IM authoritative state and HTTP history APIs
```

- [ ] **Step 2: Decide rollback route status**

Keep `gateway.ws.proxy.path: /ws/im/workers/**` in `backend/community-gateway/src/main/resources/application.yml` for one rollout. Add a comment:

```yaml
# Legacy rollback path only. Primary browser contract is /ws/im via community-im-gateway.
```

Do not delete the old gateway WS classes in this task. Deleting them should be a follow-up after runtime confidence.

- [ ] **Step 3: Run backend verification**

Run:

```bash
mvn -f backend/pom.xml -pl :community-im-gateway -am test
mvn -f backend/pom.xml -pl :community-gateway -am -Dtest=GatewayImEdgeRouteIntegrationTest,HttpRoutingIntegrationTest,WsTransparentProxyIntegrationTest test
mvn -f backend/pom.xml -pl :im-realtime -am -Dtest=SessionTicketCodecTest,ImRealtimeWebSocketIntegrationTest test
```

Expected: all commands PASS.

- [ ] **Step 4: Run frontend verification**

Run:

```bash
npm test -- src/im/imRealtimeClient.test.js
```

from `frontend/`.

Expected: PASS.

- [ ] **Step 5: Check worktree and commit docs plus final config**

Run:

```bash
git status --short
```

Expected: only files intentionally changed by this plan are listed.

Commit:

```bash
git add backend/community-gateway/src/main/resources/application.yml docs/handbook frontend/README.md
git commit -m "docs: describe community IM gateway edge"
```

---

## Final Verification

After all tasks are complete, run:

```bash
mvn -f backend/pom.xml -pl :community-im-gateway -am test
mvn -f backend/pom.xml -pl :community-gateway -am test
mvn -f backend/pom.xml -pl :im-realtime -am -Dtest=SessionTicketCodecTest,ImRealtimeWebSocketIntegrationTest test
```

Then run from `frontend/`:

```bash
npm test -- src/im/imRealtimeClient.test.js
```

If Docker is available, also run:

```bash
docker compose -f deploy/compose.yml -f deploy/compose.runtime.services.single.yml config >/tmp/community-compose-single.yml
docker compose -f deploy/compose.yml -f deploy/compose.runtime.services.cluster.yml config >/tmp/community-compose-cluster.yml
```

Expected final behavior:

- `POST /api/im/sessions` goes through `community-gateway` to `community-im-gateway`.
- Session response `wsUrl` is stable `/ws/im`.
- `WS /ws/im` goes through `community-gateway` to `community-im-gateway`.
- `community-im-gateway` bridges to the worker encoded in the ticket.
- `im-realtime` accepts only internal worker WebSocket traffic in compose runtime.
- `/api/im/conversations`, `/api/im/rooms`, and `/api/im/unread` still route to `im-core`.
