package com.nowcoder.community.content.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.content.api.model.PostSummaryView;
import com.nowcoder.community.content.dto.PostSummaryResponse;
import com.nowcoder.community.content.service.BookmarkService;
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
    private BookmarkService bookmarkService;

    private BookmarkController controller;

    @BeforeEach
    void setUp() {
        controller = new BookmarkController(bookmarkService);
    }

    @Test
    void listShouldDelegateToBookmarkServiceAndProjectViews() {
        UUID userId = uuid(7);
        UUID postId = uuid(11);
        UUID categoryId = uuid(2);
        UUID lastReplyUserId = uuid(8);
        Date createTime = new Date();
        Date lastReplyTime = new Date(createTime.getTime() + 1_000);
        PostSummaryView view = new PostSummaryView(
                postId,
                userId,
                "decoded title",
                0,
                0,
                createTime,
                3,
                9.5,
                categoryId,
                List.of("spring"),
                lastReplyUserId,
                lastReplyTime,
                lastReplyTime,
                "latest reply"
        );
        when(bookmarkService.listBookmarkedPostSummaries(userId, 0, 10)).thenReturn(List.of(view));

        Result<List<PostSummaryResponse>> result = controller.list(authentication(userId), null, null);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).singleElement().satisfies(response -> {
            assertThat(response.getId()).isEqualTo(postId);
            assertThat(response.getTitle()).isEqualTo("decoded title");
            assertThat(response.getTags()).containsExactly("spring");
            assertThat(response.getLastReplyPreview()).isEqualTo("latest reply");
        });
        verify(bookmarkService).listBookmarkedPostSummaries(userId, 0, 10);
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
