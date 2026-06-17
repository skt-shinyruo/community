package com.nowcoder.community.search.application;

import com.nowcoder.community.content.api.model.PostScanView;
import com.nowcoder.community.search.application.command.SyncPostProjectionCommand;
import com.nowcoder.community.search.domain.model.PostSearchDocument;

/**
 * Maps content scan projections onto search application/domain objects.
 */
public final class PostSearchPayloadAssembler {

    private PostSearchPayloadAssembler() {
    }

    public static PostSearchDocument toDocument(PostScanView.PostProjectionView projection) {
        return new PostSearchDocument(
                projection.postId(),
                projection.userId(),
                projection.categoryId(),
                projection.tags(),
                projection.title(),
                projection.content(),
                projection.type(),
                projection.status(),
                projection.createTime(),
                projection.score()
        );
    }

    public static SyncPostProjectionCommand toSyncCommand(PostScanView.PostProjectionView projection) {
        return new SyncPostProjectionCommand(
                projection.postId(),
                projection.userId(),
                projection.categoryId(),
                projection.tags(),
                projection.title(),
                projection.content(),
                projection.type(),
                projection.status(),
                projection.createTime(),
                projection.score()
        );
    }
}
