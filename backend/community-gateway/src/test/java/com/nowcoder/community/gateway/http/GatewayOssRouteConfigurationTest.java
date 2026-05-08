package com.nowcoder.community.gateway.http;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayOssRouteConfigurationTest {

    @Test
    void defaultHttpRoutesShouldSendOssApiAndFilesToCommunityOss() throws Exception {
        String yaml = Files.readString(Path.of("src", "main", "resources", "application.yml"));

        assertThat(yaml).contains(
                "id: oss-api",
                "path-prefix: /api/oss",
                "service-id: community-oss",
                "id: oss-files",
                "path-prefix: /files"
        );
    }
}
