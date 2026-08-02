package com.nowcoder.community.im.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "security.jwt.issuer=community-auth",
        "im.session-ticket.hmac-secret=im-gateway-test-ticket-secret-distinct-1234567890",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false"
})
class CommunityImGatewayApplicationTest {

    @org.springframework.test.context.DynamicPropertySource
    static void jwtProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("security.jwt.access-public-key", TestJwtKeys::publicKey);
    }

    @Test
    void contextLoads() {
    }
}
