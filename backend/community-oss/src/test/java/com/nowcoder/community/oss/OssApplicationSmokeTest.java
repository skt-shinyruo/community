package com.nowcoder.community.oss;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:oss;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "security.jwt.hmac-secret=01234567890123456789012345678901",
        "security.jwt.issuer=community-oss-test",
        "spring.cloud.discovery.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "oss.object-store.mode=local",
        "oss.object-store.local-root=${java.io.tmpdir}/community-oss-smoke"
})
class OssApplicationSmokeTest {

    @Test
    void contextLoads() {
    }
}
