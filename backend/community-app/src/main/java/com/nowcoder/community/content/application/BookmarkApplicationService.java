package com.nowcoder.community.content.application;

import com.nowcoder.community.content.application.port.BookmarkContentPort;
import com.nowcoder.community.content.application.result.PostSummaryResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class BookmarkApplicationService {

    private final BookmarkContentPort bookmarkContentPort;

    public BookmarkApplicationService(BookmarkContentPort bookmarkContentPort) {
        this.bookmarkContentPort = bookmarkContentPort;
    }

    public void add(UUID userId, UUID postId) {
        bookmarkContentPort.add(userId, postId);
    }

    public void remove(UUID userId, UUID postId) {
        bookmarkContentPort.remove(userId, postId);
    }

    public List<PostSummaryResult> listBookmarkedPostSummaries(UUID userId, int page, int size) {
        return bookmarkContentPort.listBookmarkedPostSummaries(userId, page, size);
    }
}
