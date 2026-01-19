package com.nowcoder.community.search.api;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SearchControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    com.nowcoder.community.search.service.ContentServiceClient contentServiceClient;

    @Test
    void searchPostsShouldBePublic() throws Exception {
        mockMvc.perform(get("/api/search/posts").param("keyword", "k"))
                .andExpect(status().isOk());
    }

    @Test
    void reindexThenSearchShouldHighlight() throws Exception {
        com.nowcoder.community.search.api.dto.ContentPostScanResponse page1 = new com.nowcoder.community.search.api.dto.ContentPostScanResponse();
        com.nowcoder.community.common.event.payload.PostPayload post = new com.nowcoder.community.common.event.payload.PostPayload();
        post.setPostId(1);
        post.setUserId(1);
        post.setTitle("hello title");
        post.setContent("hello content");
        post.setCreateTime(Instant.now());
        page1.setItems(List.of(post));
        page1.setNextAfterId(1);
        page1.setHasMore(false);

        com.nowcoder.community.search.api.dto.ContentPostScanResponse page2 = new com.nowcoder.community.search.api.dto.ContentPostScanResponse();
        page2.setItems(List.of());
        page2.setNextAfterId(1);
        page2.setHasMore(false);

        when(contentServiceClient.scanPosts(anyInt(), anyInt())).thenReturn(page1, page2);

        SignedJWT jwt = new SignedJWT(
                new JWSHeader(JWSAlgorithm.HS256),
                new JWTClaimsSet.Builder()
                        .subject("1")
                        .issueTime(java.util.Date.from(Instant.now()))
                        .expirationTime(java.util.Date.from(Instant.now().plusSeconds(600)))
                        .claim("username", "u1")
                        .claim("authorities", List.of("ROLE_ADMIN"))
                        .build()
        );
        jwt.sign(new MACSigner("test-jwt-secret-please-change-at-least-32bytes".getBytes(StandardCharsets.UTF_8)));
        String token = jwt.serialize();

        mockMvc.perform(post("/api/search/internal/reindex")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        String resp = mockMvc.perform(get("/api/search/posts").param("keyword", "hello"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(resp).contains("<em>hello</em>");
    }

    @Test
    void internalReindexShouldRequireInternalToken() throws Exception {
        mockMvc.perform(post("/internal/search/reindex"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/internal/search/reindex").header("X-Internal-Token", "wrong"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/internal/search/reindex").header("X-Internal-Token", "test-search-internal-token"))
                .andExpect(status().isOk());
    }
}
