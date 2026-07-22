package com.nowcoder.community.im.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "security.jwt.hmac-secret=im-gateway-test-jwt-secret-please-change-123456",
        "security.jwt.issuer=community-auth",
        "im.session-ticket.hmac-secret=im-gateway-test-ticket-secret-distinct-1234567890",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false"
})
class CommunityImGatewayApplicationTest {

    @Test
    void contextLoads() {
    }
}
