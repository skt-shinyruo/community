package com.nowcoder.community.content.service;

import com.nowcoder.community.content.api.model.PostSummaryView;
import com.nowcoder.community.content.dto.PostSummaryResponse;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookmarkApplicationServiceTest {

    @Test
    void listBookmarkedPostSummaryResponsesShouldMapViewsToDtosAndDelegate() {
        BookmarkService bookmarkService = mock(BookmarkService.class);
        BookmarkApplicationService service = new BookmarkApplicationService(bookmarkService);
        UUID userId = uuid(7);
        UUID postId = uuid(11);
        UUID categoryId = uuid(2);
        UUID lastReplyUserId = uuid(8);
        Date createTime = new Date();
        Date lastActivityTime = new Date(createTime.getTime() + 2_000);
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
                new Date(createTime.getTime() + 1_000),
                lastActivityTime,
                "latest reply"
        );
        when(bookmarkService.listBookmarkedPostSummaries(userId, 0, 10)).thenReturn(List.of(view));

        List<PostSummaryResponse> result = service.listBookmarkedPostSummaryResponses(userId, 0, 10);

        assertThat(result).singleElement().satisfies(response -> {
            assertThat(response.getId()).isEqualTo(postId);
            assertThat(response.getUserId()).isEqualTo(userId);
            assertThat(response.getTitle()).isEqualTo("decoded title");
            assertThat(response.getTags()).containsExactly("spring");
            assertThat(response.getLastReplyPreview()).isEqualTo("latest reply");
            assertThat(response.getLastActivityTime()).isEqualTo(lastActivityTime);
        });
        verify(bookmarkService).listBookmarkedPostSummaries(userId, 0, 10);
    }
}
