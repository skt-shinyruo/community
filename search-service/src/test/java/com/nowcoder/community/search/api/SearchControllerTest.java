package com.nowcoder.community.search.api;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SearchControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void searchPostsShouldBePublic() throws Exception {
        mockMvc.perform(get("/api/search/posts").param("keyword", "k"))
                .andExpect(status().isOk());
    }

    @Test
    void reindexThenSearchShouldHighlight() throws Exception {
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
