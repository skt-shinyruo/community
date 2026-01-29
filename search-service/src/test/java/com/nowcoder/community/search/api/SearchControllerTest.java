package com.nowcoder.community.search.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "ops.guard.search-reindex.enabled=true",
        "ops.guard.search-reindex.allowlist=127.0.0.1/32,0:0:0:0:0:0:0:1/128",
        "ops.search.token=test-search-ops-token"
})
@AutoConfigureMockMvc
class SearchControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    com.nowcoder.community.search.service.ContentServiceClient contentServiceClient;

    @MockBean
    StringRedisTemplate redisTemplate;

    private final Map<String, String> kv = new ConcurrentHashMap<>();
    private final Map<String, Long> counters = new ConcurrentHashMap<>();

    @BeforeEach
    void setupRedisMock() {
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);

        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            if (kv.containsKey(key)) {
                return false;
            }
            kv.put(key, "1");
            return true;
        }).when(ops).setIfAbsent(anyString(), anyString(), any(Duration.class));

        when(ops.increment(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            long next = counters.getOrDefault(key, 0L) + 1;
            counters.put(key, next);
            return next;
        });

        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);

        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            kv.remove(key);
            return true;
        }).when(redisTemplate).delete(anyString());
    }

    @Test
    void searchPostsShouldBePublic() throws Exception {
        mockMvc.perform(get("/api/search/posts").param("keyword", "k"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/search/posts").param("keyword", "k").param("categoryId", "1").param("tag", "Java"))
                .andExpect(status().isOk());
    }

    @Test
    void reindexThenSearchShouldHighlight() throws Exception {
        com.nowcoder.community.search.api.dto.ContentPostScanResponse page1 = new com.nowcoder.community.search.api.dto.ContentPostScanResponse();
        com.nowcoder.community.common.event.payload.PostPayload post = new com.nowcoder.community.common.event.payload.PostPayload();
        post.setPostId(1);
        post.setUserId(1);
        post.setCategoryId(1);
        post.setTags(List.of("Java", "Spring"));
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

        mockMvc.perform(post("/internal/search/reindex")
                        .header("X-Internal-Token", "test-search-internal-token")
                        .header("X-Ops-Token", "test-search-ops-token"))
                .andExpect(status().isOk());

        String resp = mockMvc.perform(get("/api/search/posts").param("keyword", "hello"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(resp).contains("<em>hello</em>");
    }

    @Test
    void searchShouldSupportPagingAndOrderingInMemoryRepo() throws Exception {
        // Arrange: 2 posts with different createTime -> expect newer first
        com.nowcoder.community.search.api.dto.ContentPostScanResponse page1 = new com.nowcoder.community.search.api.dto.ContentPostScanResponse();

        com.nowcoder.community.common.event.payload.PostPayload p1 = new com.nowcoder.community.common.event.payload.PostPayload();
        p1.setPostId(1);
        p1.setUserId(1);
        p1.setCategoryId(1);
        p1.setTags(List.of("Java"));
        p1.setTitle("old title");
        p1.setContent("old content");
        p1.setCreateTime(Instant.now().minusSeconds(3600));

        com.nowcoder.community.common.event.payload.PostPayload p2 = new com.nowcoder.community.common.event.payload.PostPayload();
        p2.setPostId(2);
        p2.setUserId(1);
        p2.setCategoryId(2);
        p2.setTags(List.of("Spring"));
        p2.setTitle("new title");
        p2.setContent("new content");
        p2.setCreateTime(Instant.now());

        page1.setItems(List.of(p1, p2));
        page1.setNextAfterId(2);
        page1.setHasMore(false);

        com.nowcoder.community.search.api.dto.ContentPostScanResponse page2 = new com.nowcoder.community.search.api.dto.ContentPostScanResponse();
        page2.setItems(List.of());
        page2.setNextAfterId(2);
        page2.setHasMore(false);

        when(contentServiceClient.scanPosts(anyInt(), anyInt())).thenReturn(page1, page2);

        mockMvc.perform(post("/internal/search/reindex")
                        .header("X-Internal-Token", "test-search-internal-token")
                        .header("X-Ops-Token", "test-search-ops-token"))
                .andExpect(status().isOk());

        // Page 0 size 1 -> p2 (newer)
        String resp0 = mockMvc.perform(get("/api/search/posts").param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode data0 = objectMapper.readTree(resp0).path("data");
        assertThat(data0.isArray()).isTrue();
        assertThat(data0.size()).isEqualTo(1);
        assertThat(data0.get(0).path("postId").asInt()).isEqualTo(2);

        // Page 1 size 1 -> p1 (older)
        String resp1 = mockMvc.perform(get("/api/search/posts").param("page", "1").param("size", "1"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode data1 = objectMapper.readTree(resp1).path("data");
        assertThat(data1.isArray()).isTrue();
        assertThat(data1.size()).isEqualTo(1);
        assertThat(data1.get(0).path("postId").asInt()).isEqualTo(1);

        // Tag filter should work with '#' prefix
        String respTag = mockMvc.perform(get("/api/search/posts").param("tag", "#spring"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode dataTag = objectMapper.readTree(respTag).path("data");
        assertThat(dataTag.isArray()).isTrue();
        assertThat(dataTag.size()).isEqualTo(1);
        assertThat(dataTag.get(0).path("postId").asInt()).isEqualTo(2);
    }

    @Test
    void internalReindexShouldRequireInternalToken() throws Exception {
        mockMvc.perform(post("/internal/search/reindex"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/internal/search/reindex").header("X-Internal-Token", "wrong"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/internal/search/reindex").header("X-Internal-Token", "test-search-internal-token"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/internal/search/reindex")
                        .header("X-Internal-Token", "test-search-internal-token")
                        .header("X-Ops-Token", "wrong"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/internal/search/reindex")
                        .header("X-Internal-Token", "test-search-internal-token")
                        .header("X-Ops-Token", "test-search-ops-token"))
                .andExpect(status().isOk());
    }
}
