package com.nowcoder.community.content.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nowcoder.community.content.api.dto.CreateCommentRequest;
import com.nowcoder.community.content.api.dto.CreatePostRequest;
import com.nowcoder.community.content.service.UserModerationClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "content.storage=memory",
        "content.events.publisher=memory",
        "content.score.refresh.enabled=false",
        "security.jwt.hmac-secret=test-jwt-secret-please-change-at-least-32bytes",
        "spring.datasource.url=jdbc:h2:mem:content;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;NON_KEYWORDS=USER",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema.sql",
        "spring.kafka.listener.auto-startup=false",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false"
})
@AutoConfigureMockMvc
class PostControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    UserModerationClient userModerationClient;

    @BeforeEach
    void stubUserModerationClient() {
        when(userModerationClient.getStatus(anyInt())).thenAnswer(invocation -> {
            Integer userId = invocation.getArgument(0, Integer.class);
            UserModerationClient.ModerationStatus status = new UserModerationClient.ModerationStatus();
            status.setUserId(userId == null ? 0 : userId);
            status.setMuteUntil(null);
            status.setBanUntil(null);
            return status;
        });
    }

    @Test
    void listShouldBePublic() throws Exception {
        mockMvc.perform(get("/api/posts"))
                .andExpect(status().isOk());
    }

    @Test
    void categoriesAndHotTagsShouldBePublic() throws Exception {
        String categoriesResp = mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Map<?, ?> categories = objectMapper.readValue(categoriesResp, Map.class);
        List<?> categoryItems = (List<?>) categories.get("data");
        assertThat(categoryItems).isNotEmpty();

        String hotTagsResp = mockMvc.perform(get("/api/tags/hot").param("limit", "8"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Map<?, ?> hotTags = objectMapper.readValue(hotTagsResp, Map.class);
        List<?> hotItems = (List<?>) hotTags.get("data");
        assertThat(hotItems).isNotNull();

        mockMvc.perform(get("/api/tags/suggest").param("q", "Ja").param("limit", "8"))
                .andExpect(status().isOk());
    }

    @Test
    void createThenQueryShouldWork() throws Exception {
        String token = tokenForUser(1);

        CreatePostRequest req = new CreatePostRequest();
        req.setTitle("hello<script>alert(1)</script>");
        req.setContent("badword <b>bold</b>");
        req.setCategoryId(1);
        req.setTags(List.of("Java", "Spring"));

        String createResp = mockMvc.perform(post("/api/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        int postId = ((Number) ((Map<?, ?>) objectMapper.readValue(createResp, Map.class).get("data")).get("postId")).intValue();

        mockMvc.perform(get("/api/posts"))
                .andExpect(status().isOk());

        String detailResp = mockMvc.perform(get("/api/posts/" + postId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Map<?, ?> detail = objectMapper.readValue(detailResp, Map.class);
        Map<?, ?> detailData = (Map<?, ?>) detail.get("data");
        assertThat(String.valueOf(detailData.get("title"))).contains("&lt;script&gt;");
        assertThat(String.valueOf(detailData.get("content"))).doesNotContain("<b>").contains("&lt;b&gt;");
        assertThat(String.valueOf(detailData.get("content"))).contains("***");
        assertThat(((Number) detailData.get("categoryId")).intValue()).isEqualTo(1);
        assertThat((List<?>) detailData.get("tags")).isNotEmpty();
        assertThat(((List<?>) detailData.get("tags")).stream().map(String::valueOf).toList()).contains("Java", "Spring");

        // 列表：category/tag 过滤应生效
        String listResp = mockMvc.perform(get("/api/posts").param("categoryId", "1").param("tag", "Java"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Map<?, ?> list = objectMapper.readValue(listResp, Map.class);
        List<?> listItems = (List<?>) list.get("data");
        assertThat(listItems).isNotEmpty();
        Map<?, ?> firstPost = (Map<?, ?>) listItems.get(0);
        assertThat(((Number) firstPost.get("categoryId")).intValue()).isEqualTo(1);
        assertThat(((List<?>) firstPost.get("tags")).stream().map(String::valueOf).toList()).contains("Java");

        CreateCommentRequest c = new CreateCommentRequest();
        c.setContent("<img src=x onerror=alert(1)> badword");
        mockMvc.perform(post("/api/posts/" + postId + "/comments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(c)))
                .andExpect(status().isOk());

        String commentsResp = mockMvc.perform(get("/api/posts/" + postId + "/comments"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Map<?, ?> comments = objectMapper.readValue(commentsResp, Map.class);
        List<?> items = (List<?>) comments.get("data");
        assertThat(items).isNotEmpty();
        Map<?, ?> first = (Map<?, ?>) items.get(0);
        assertThat(String.valueOf(first.get("content"))).contains("&lt;img").contains("***");

        // 热门标签：应聚合出刚写入的 tags
        String hotTagsResp2 = mockMvc.perform(get("/api/tags/hot").param("limit", "10"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Map<?, ?> hotTags2 = objectMapper.readValue(hotTagsResp2, Map.class);
        List<?> hotItems2 = (List<?>) hotTags2.get("data");
        assertThat(hotItems2).extracting(m -> ((Map<?, ?>) m).get("name"), m -> ((Map<?, ?>) m).get("useCount"))
                .contains(tuple("Java", 1), tuple("Spring", 1));
    }

    private String tokenForUser(int userId) throws Exception {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader(JWSAlgorithm.HS256),
                new JWTClaimsSet.Builder()
                        .subject(String.valueOf(userId))
                        .issueTime(java.util.Date.from(Instant.now()))
                        .expirationTime(java.util.Date.from(Instant.now().plusSeconds(600)))
                        .claim("username", "u" + userId)
                        .claim("authorities", List.of("ROLE_USER"))
                        .build()
        );
        jwt.sign(new MACSigner("test-jwt-secret-please-change-at-least-32bytes".getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }
}
