package com.nowcoder.community.app.security;

import com.nowcoder.community.content.api.model.PostSummaryView;
import com.nowcoder.community.content.api.model.RecentUserCommentView;
import com.nowcoder.community.content.api.query.PostReadQueryApi;
import com.nowcoder.community.user.api.model.UserProfileView;
import com.nowcoder.community.user.service.AvatarService;
import com.nowcoder.community.user.service.UserProfileApplicationService;
import com.nowcoder.community.user.service.UserProfilePageView;
import com.nowcoder.community.user.service.UserReadApplicationService;
import com.nowcoder.community.user.service.UserSocialProfileService;
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
    private PostReadQueryApi postReadQueryApi;

    @MockBean
    private UserReadApplicationService userReadApplicationService;

    @MockBean
    private UserProfileApplicationService userProfileApplicationService;

    @MockBean
    private AvatarService avatarService;

    @MockBean
    private UserSocialProfileService userSocialProfileService;

    @Test
    void unauthenticatedBatchPostSummaryShouldBeAllowed() throws Exception {
        when(postReadQueryApi.listPostsByIds(anyList())).thenReturn(List.<PostSummaryView>of());

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
        when(userReadApplicationService.getProfile(USER_ID))
                .thenReturn(new UserProfileView(USER_ID, "u42", "h42", 0, 0, new Date(), 0, 1, 0L, "UNKNOWN"));
        when(userProfileApplicationService.listRecentPosts(eq(USER_ID), any(), any()))
                .thenReturn(List.<UserProfilePageView.RecentPostSummaryView>of());
        when(userProfileApplicationService.listRecentComments(eq(USER_ID), any(), any()))
                .thenReturn(List.<UserProfilePageView.RecentCommentItemView>of());

        mockMvc.perform(get("/api/users/" + USER_ID + "/recent-posts"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/" + USER_ID + "/recent-comments"))
                .andExpect(status().isOk());
    }

    @Test
    void directCommunityAppReadEndpointShouldNotEmitCorsHeaders() throws Exception {
        when(userProfileApplicationService.listRecentPosts(eq(USER_ID), any(), any()))
                .thenReturn(List.<UserProfilePageView.RecentPostSummaryView>of());

        mockMvc.perform(get("/api/users/" + USER_ID + "/recent-posts").header("Origin", "http://localhost:12881"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }
}
