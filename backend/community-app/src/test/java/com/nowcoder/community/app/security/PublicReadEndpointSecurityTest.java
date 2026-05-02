package com.nowcoder.community.app.security;

import com.nowcoder.community.content.application.result.PostSummaryResult;
import com.nowcoder.community.content.application.PostPublishingApplicationService;
import com.nowcoder.community.content.application.PostModerationApplicationService;
import com.nowcoder.community.content.application.BookmarkApplicationService;
import com.nowcoder.community.content.application.CommentApplicationService;
import com.nowcoder.community.content.application.CommentReadApplicationService;
import com.nowcoder.community.content.application.PostReadApplicationService;
import com.nowcoder.community.user.application.UserProfileApplicationService;
import com.nowcoder.community.user.application.UserReadApplicationService;
import com.nowcoder.community.user.application.result.UserProfilePageResult;
import com.nowcoder.community.user.infrastructure.avatar.AvatarService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Date;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static com.nowcoder.community.support.TestUuids.uuid;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "gateway.origin-guard.allowed-origins[0]=http://localhost:12881"
})
class PublicReadEndpointSecurityTest {

    private static final UUID USER_ID = uuid(42);

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PostReadApplicationService postReadApplicationService;

    @MockBean
    private CommentReadApplicationService commentReadApplicationService;

    @MockBean
    private PostPublishingApplicationService postPublishingApplicationService;

    @MockBean
    private PostModerationApplicationService postModerationApplicationService;

    @MockBean
    private CommentApplicationService commentApplicationService;

    @MockBean
    private BookmarkApplicationService bookmarkApplicationService;

    @MockBean
    private UserReadApplicationService userReadApplicationService;

    @MockBean
    private UserProfileApplicationService userProfileApplicationService;

    @MockBean
    private AvatarService avatarService;

    @Test
    void unauthenticatedBatchPostSummaryShouldBeAllowed() throws Exception {
        when(postReadApplicationService.listPostsByIds(anyList())).thenReturn(List.<PostSummaryResult>of());

        mockMvc.perform(post("/api/posts/batch-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "postIds": ["%s", "%s"]
                                }
                                """.formatted(uuid(1), uuid(2))))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedRecentActivityEndpointsShouldBeAllowed() throws Exception {
        when(userProfileApplicationService.listRecentPosts(eq(USER_ID), any(), any()))
                .thenReturn(List.<UserProfilePageResult.RecentPostSummaryResult>of());
        when(userProfileApplicationService.listRecentComments(eq(USER_ID), any(), any()))
                .thenReturn(List.<UserProfilePageResult.RecentCommentItemResult>of());

        mockMvc.perform(get("/api/users/" + USER_ID + "/recent-posts"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/" + USER_ID + "/recent-comments"))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedUserProfileShouldBeAllowed() throws Exception {
        when(userProfileApplicationService.get(any(), eq(USER_ID)))
                .thenReturn(new UserProfilePageResult(
                        USER_ID,
                        "u42",
                        "h42",
                        0,
                        0,
                        new Date(),
                        0,
                        1,
                        false,
                        null,
                        null,
                        0L,
                        0L,
                        0L,
                        false,
                        false
                ));

        mockMvc.perform(get("/api/users/" + USER_ID))
                .andExpect(status().isOk());
    }

    @Test
    void directCommunityAppReadEndpointShouldNotEmitCorsHeaders() throws Exception {
        when(userProfileApplicationService.listRecentPosts(eq(USER_ID), any(), any()))
                .thenReturn(List.<UserProfilePageResult.RecentPostSummaryResult>of());

        mockMvc.perform(get("/api/users/" + USER_ID + "/recent-posts").header("Origin", "http://localhost:12881"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    @Test
    void directUserProfileReadEndpointShouldNotEmitCorsHeaders() throws Exception {
        when(userProfileApplicationService.get(any(), eq(USER_ID)))
                .thenReturn(new UserProfilePageResult(
                        USER_ID,
                        "u42",
                        "h42",
                        0,
                        0,
                        new Date(),
                        0,
                        1,
                        false,
                        null,
                        null,
                        0L,
                        0L,
                        0L,
                        false,
                        false
                ));

        mockMvc.perform(get("/api/users/" + USER_ID).header("Origin", "http://localhost:12881"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }
}
