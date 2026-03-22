package com.nowcoder.community.app.security;

import com.nowcoder.community.content.dto.UserRecentCommentResponse;
import com.nowcoder.community.content.service.PostFacadeService;
import com.nowcoder.community.user.entity.User;
import com.nowcoder.community.user.service.AvatarService;
import com.nowcoder.community.user.service.PointsService;
import com.nowcoder.community.user.service.UserService;
import com.nowcoder.community.user.service.UserSocialProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicReadEndpointSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PostFacadeService postFacadeService;

    @MockBean
    private UserService userService;

    @MockBean
    private AvatarService avatarService;

    @MockBean
    private UserSocialProfileService userSocialProfileService;

    @MockBean
    private PointsService pointsService;

    @Test
    void unauthenticatedBatchPostSummaryShouldBeAllowed() throws Exception {
        when(postFacadeService.listPostsByIds(anyList())).thenReturn(List.of());

        mockMvc.perform(post("/api/posts/batch-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"postIds\":[1,2]}"))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedRecentActivityEndpointsShouldBeAllowed() throws Exception {
        User user = new User();
        user.setId(42);
        user.setUsername("u42");

        when(userService.getById(42)).thenReturn(user);
        when(postFacadeService.listPostsByUser(anyInt(), any(), any())).thenReturn(List.of());
        when(postFacadeService.listRecentCommentsByUser(anyInt(), any(), any())).thenReturn(List.<UserRecentCommentResponse>of());

        mockMvc.perform(get("/api/users/42/recent-posts"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/42/recent-comments"))
                .andExpect(status().isOk());
    }
}
