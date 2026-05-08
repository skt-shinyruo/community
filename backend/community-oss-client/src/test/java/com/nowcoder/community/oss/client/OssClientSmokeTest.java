package com.nowcoder.community.oss.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OssClientSmokeTest {

    @Test
    void clientTypesCompile() {
        assertThat(CommunityOssClient.class).isNotNull();
    }
}
