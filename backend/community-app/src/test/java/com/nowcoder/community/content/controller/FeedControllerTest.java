package com.nowcoder.community.content.controller;

import com.nowcoder.community.common.web.GlobalExceptionHandler;
import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.content.application.FeedReadApplicationService;
import com.nowcoder.community.content.application.FollowFeedReadApplicationService;
import com.nowcoder.community.content.application.result.FeedPageResult;
import com.nowcoder.community.content.controller.dto.FeedPageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.List;
import java.time.Instant;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FeedControllerTest {

    @Test
    void legacyPostsListRouteShouldNoLongerBeHandledByPostController() {
        assertFalse(Arrays.stream(PostController.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().equals("list")));
    }

    @Test
    void globalFeedShouldDelegateCursorRead() throws Exception {
        FeedReadApplicationService feedReadApplicationService = mock(FeedReadApplicationService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FeedController(feedReadApplicationService, mock(FollowFeedReadApplicationService.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        when(feedReadApplicationService.listGlobalHotFeed(null, "cursor-1", 20))
                .thenReturn(new FeedPageResult(List.of(), "cursor-2", "rank-v1"));

        mockMvc.perform(get("/api/feed/global")
                        .param("cursor", "cursor-1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nextCursor").value("cursor-2"))
                .andExpect(jsonPath("$.data.rankVersion").value("rank-v1"));

        verify(feedReadApplicationService).listGlobalHotFeed(null, "cursor-1", 20);
    }

    @Test
    void boardFeedShouldDelegateBoardCursorRead() throws Exception {
        FeedReadApplicationService feedReadApplicationService = mock(FeedReadApplicationService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FeedController(feedReadApplicationService, mock(FollowFeedReadApplicationService.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        UUID boardId = uuid(9);

        when(feedReadApplicationService.listBoardHotFeed(isNull(), eq(boardId), eq("cursor-9"), eq(12)))
                .thenReturn(new FeedPageResult(List.of(), "", "rank-board-v1"));

        mockMvc.perform(get("/api/boards/{boardId}/feed", boardId)
                        .param("cursor", "cursor-9")
                        .param("size", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nextCursor").value(""))
                .andExpect(jsonPath("$.data.rankVersion").value("rank-board-v1"));

        verify(feedReadApplicationService).listBoardHotFeed(null, boardId, "cursor-9", 12);
    }

    @Test
    void followFeedShouldDelegateAuthenticatedCursorRead() throws Exception {
        FeedReadApplicationService feedReadApplicationService = mock(FeedReadApplicationService.class);
        FollowFeedReadApplicationService followFeedReadApplicationService = mock(FollowFeedReadApplicationService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FeedController(feedReadApplicationService, followFeedReadApplicationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        UUID viewerId = uuid(13);

        when(followFeedReadApplicationService.listFollowFeed(eq(viewerId), eq("cursor-f"), eq(12)))
                .thenReturn(new FeedPageResult(List.of(), "", "follow-v1"));

        mockMvc.perform(get("/api/feed/follow")
                        .principal(jwtAuthentication(viewerId))
                        .param("cursor", "cursor-f")
                        .param("size", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rankVersion").value("follow-v1"));

        verify(followFeedReadApplicationService).listFollowFeed(viewerId, "cursor-f", 12);
    }

    private Authentication jwtAuthentication(UUID userId) {
        Jwt jwt = Jwt.withTokenValue("token-" + userId)
                .header("alg", "none")
                .subject(userId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(jwt);
        return authentication;
    }
}
