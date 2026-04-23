package com.nowcoder.community.content.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.content.dto.PostSummaryResponse;
import com.nowcoder.community.content.service.BookmarkApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookmarkControllerTest {

    @Mock
    private BookmarkApplicationService bookmarkApplicationService;

    private BookmarkController controller;

    @BeforeEach
    void setUp() {
        controller = new BookmarkController(bookmarkApplicationService);
    }

    @Test
    void listShouldDelegateToBookmarkApplicationServiceAndReturnHttpDtos() {
        UUID userId = uuid(7);
        UUID postId = uuid(11);
        UUID categoryId = uuid(2);
        UUID lastReplyUserId = uuid(8);
        Date createTime = new Date();
        Date lastReplyTime = new Date(createTime.getTime() + 1_000);
        PostSummaryResponse dto = new PostSummaryResponse();
        dto.setId(postId);
        dto.setUserId(userId);
        dto.setTitle("decoded title");
        dto.setType(0);
        dto.setStatus(0);
        dto.setCreateTime(createTime);
        dto.setCommentCount(3);
        dto.setScore(9.5);
        dto.setCategoryId(categoryId);
        dto.setTags(List.of("spring"));
        dto.setLastReplyUserId(lastReplyUserId);
        dto.setLastReplyTime(lastReplyTime);
        dto.setLastActivityTime(lastReplyTime);
        dto.setLastReplyPreview("latest reply");
        when(bookmarkApplicationService.listBookmarkedPostSummaryResponses(userId, 0, 10)).thenReturn(List.of(dto));

        Result<List<PostSummaryResponse>> result = controller.list(authentication(userId), null, null);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).singleElement().satisfies(response -> {
            assertThat(response.getId()).isEqualTo(postId);
            assertThat(response.getTitle()).isEqualTo("decoded title");
            assertThat(response.getTags()).containsExactly("spring");
            assertThat(response.getLastReplyPreview()).isEqualTo("latest reply");
        });
        verify(bookmarkApplicationService).listBookmarkedPostSummaryResponses(userId, 0, 10);
    }

    @Test
    void bookmarkAndUnbookmarkShouldDelegateToApplicationService() {
        UUID userId = uuid(7);
        UUID postId = uuid(11);

        Result<Void> bookmarkResult = controller.bookmark(authentication(userId), postId);
        Result<Void> unbookmarkResult = controller.unbookmark(authentication(userId), postId);

        assertThat(bookmarkResult.getCode()).isEqualTo(0);
        assertThat(unbookmarkResult.getCode()).isEqualTo(0);
        verify(bookmarkApplicationService).add(userId, postId);
        verify(bookmarkApplicationService).remove(userId, postId);
    }

    private Authentication authentication(UUID userId) {
        Jwt jwt = Jwt.withTokenValue("token-" + userId)
                .header("alg", "none")
                .subject(userId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(jwt);
        return authentication;
    }
}
