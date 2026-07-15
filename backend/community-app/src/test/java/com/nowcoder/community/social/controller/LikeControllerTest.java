package com.nowcoder.community.social.controller;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.web.GlobalExceptionHandler;
import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.social.application.LikeApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LikeControllerTest {

    @Test
    void likeReadEndpointsShouldDelegateUnsupportedEntityTypeToApplicationService() {
        LikeApplicationService likeApplicationService = mock(LikeApplicationService.class);
        LikeController controller = new LikeController(likeApplicationService);
        UUID userId = uuid(7);
        UUID entityId = uuid(11);
        when(likeApplicationService.isLiked(userId, 999, entityId)).thenReturn(false);
        when(likeApplicationService.count(999, entityId)).thenReturn(5L);
        when(likeApplicationService.counts(999, List.of(entityId))).thenReturn(Map.of(entityId, 5L));
        when(likeApplicationService.statuses(userId, 999, List.of(entityId))).thenReturn(Map.of(entityId, false));

        assertThat(controller.status(authentication(userId), 999, entityId).getData()).isFalse();
        assertThat(controller.count(999, entityId).getData()).isEqualTo(5L);
        assertThat(controller.counts(999, List.of(entityId)).getData()).containsEntry(entityId, 5L);
        assertThat(controller.statuses(authentication(userId), 999, List.of(entityId)).getData()).containsEntry(entityId, false);

        verify(likeApplicationService).isLiked(userId, 999, entityId);
        verify(likeApplicationService).count(999, entityId);
        verify(likeApplicationService).counts(999, List.of(entityId));
        verify(likeApplicationService).statuses(userId, 999, List.of(entityId));
    }

    @Test
    void countsShouldBindCommaSeparatedUuidList() throws Exception {
        LikeApplicationService likeApplicationService = mock(LikeApplicationService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new LikeController(likeApplicationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        UUID firstEntityId = uuid(11);
        UUID secondEntityId = uuid(12);
        when(likeApplicationService.counts(EntityTypes.POST, List.of(firstEntityId, secondEntityId)))
                .thenReturn(Map.of(firstEntityId, 2L, secondEntityId, 3L));

        mockMvc.perform(get("/api/likes/counts")
                        .param("entityType", String.valueOf(EntityTypes.POST))
                        .param("entityIds", firstEntityId + "," + secondEntityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data['" + firstEntityId + "']").value(2))
                .andExpect(jsonPath("$.data['" + secondEntityId + "']").value(3));

        verify(likeApplicationService).counts(EntityTypes.POST, List.of(firstEntityId, secondEntityId));
    }

    @Test
    void countsShouldRejectInvalidUuidBeforeApplicationService() throws Exception {
        LikeApplicationService likeApplicationService = mock(LikeApplicationService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new LikeController(likeApplicationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/api/likes/counts")
                        .param("entityType", String.valueOf(EntityTypes.POST))
                        .param("entityIds", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.INVALID_ARGUMENT.getCode()));

        verifyNoInteractions(likeApplicationService);
    }

    private Authentication authentication(UUID userId) {
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
