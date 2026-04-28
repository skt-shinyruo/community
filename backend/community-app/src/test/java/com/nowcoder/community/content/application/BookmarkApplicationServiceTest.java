package com.nowcoder.community.content.application;

import com.nowcoder.community.content.application.result.PostSummaryResult;
import com.nowcoder.community.content.application.port.BookmarkContentPort;
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
    void listBookmarkedPostSummariesShouldDelegate() {
        BookmarkContentPort bookmarkContentPort = mock(BookmarkContentPort.class);
        BookmarkApplicationService service = new BookmarkApplicationService(bookmarkContentPort);
        UUID userId = uuid(7);
        UUID postId = uuid(11);
        UUID categoryId = uuid(2);
        UUID lastReplyUserId = uuid(8);
        Date createTime = new Date();
        Date lastActivityTime = new Date(createTime.getTime() + 2_000);
        PostSummaryResult view = new PostSummaryResult(
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
        when(bookmarkContentPort.listBookmarkedPostSummaries(userId, 0, 10)).thenReturn(List.of(view));

        List<PostSummaryResult> result = service.listBookmarkedPostSummaries(userId, 0, 10);

        assertThat(result).singleElement().satisfies(summary -> {
            assertThat(summary.id()).isEqualTo(postId);
            assertThat(summary.userId()).isEqualTo(userId);
            assertThat(summary.title()).isEqualTo("decoded title");
            assertThat(summary.tags()).containsExactly("spring");
            assertThat(summary.lastReplyPreview()).isEqualTo("latest reply");
            assertThat(summary.lastActivityTime()).isEqualTo(lastActivityTime);
        });
        verify(bookmarkContentPort).listBookmarkedPostSummaries(userId, 0, 10);
    }
}
