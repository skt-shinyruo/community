package com.nowcoder.community.content.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.application.command.RecordPostViewCommand;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.model.PostCounterSnapshot;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import com.nowcoder.community.content.domain.repository.PostCounterSnapshotRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class PostCounterApplicationService {

    private final PostCounterCache postCounterCache;
    private final PostCounterSnapshotRepository postCounterSnapshotRepository;
    private final PostContentRepository postContentRepository;
    private final LikeQueryPort likeQueryPort;

    PostCounterApplicationService(PostCounterCache postCounterCache) {
        this(postCounterCache, null, null, null);
    }

    @Autowired
    public PostCounterApplicationService(
            PostCounterCache postCounterCache,
            PostCounterSnapshotRepository postCounterSnapshotRepository,
            PostContentRepository postContentRepository,
            LikeQueryPort likeQueryPort
    ) {
        this.postCounterCache = postCounterCache;
        this.postCounterSnapshotRepository = postCounterSnapshotRepository;
        this.postContentRepository = postContentRepository;
        this.likeQueryPort = likeQueryPort;
    }

    public void recordView(RecordPostViewCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (command.postId() == null || command.viewerKey() == null || command.viewerKey().isBlank()) {
            return;
        }
        if (!postCounterCache.markViewerSeen(command.postId(), command.viewerKey(), command.viewedAt())) {
            return;
        }
        postCounterCache.incrementViewCount(command.postId());
    }

    public PostCounterSnapshot read(UUID postId) {
        if (postId == null) {
            return PostCounterSnapshot.empty();
        }
        PostCounterSnapshot cached = defaultIfNull(postCounterCache.get(postId), postId);
        if (postContentRepository == null || likeQueryPort == null) {
            return cached;
        }
        try {
            DiscussPost post = postContentRepository.getByIdAllowDeleted(postId);
            return new PostCounterSnapshot(
                    postId,
                    cached.viewCount(),
                    likeQueryPort.countPostLikes(postId),
                    post == null ? cached.commentCount() : Math.max(0L, post.getCommentCount()),
                    cached.bookmarkCount(),
                    post == null ? cached.score() : Math.max(0.0, post.getScore())
            );
        } catch (BusinessException ex) {
            return cached;
        }
    }

    public int flushSnapshots(int batchSize) {
        if (postCounterSnapshotRepository == null) {
            return 0;
        }
        int safeBatchSize = Math.max(1, Math.min(batchSize, 500));
        List<PostCounterCache.DirtyPost> requested = postCounterCache.dirtyPosts(safeBatchSize);
        if (requested == null || requested.isEmpty()) {
            return 0;
        }
        List<PostCounterCache.DirtyPost> flushed = new ArrayList<>();
        for (PostCounterCache.DirtyPost dirtyPost : requested) {
            if (dirtyPost == null || dirtyPost.postId() == null) {
                continue;
            }
            UUID postId = dirtyPost.postId();
            PostCounterSnapshot snapshot = read(postId);
            postCounterSnapshotRepository.upsert(
                    postId,
                    snapshot.viewCount(),
                    snapshot.likeCount(),
                    snapshot.commentCount(),
                    snapshot.bookmarkCount(),
                    snapshot.score()
            );
            flushed.add(dirtyPost);
        }
        if (!flushed.isEmpty()) {
            postCounterCache.clearDirtyPosts(flushed);
        }
        return flushed.size();
    }

    private static PostCounterSnapshot defaultIfNull(PostCounterSnapshot snapshot, UUID postId) {
        return snapshot == null ? new PostCounterSnapshot(postId, 0L, 0L, 0L, 0L, 0.0) : snapshot;
    }
}
