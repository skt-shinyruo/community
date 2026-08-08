package com.nowcoder.community.content.infrastructure.persistence.dataobject;

import java.util.UUID;

public class BookmarkCounterReconciliationDataObject {

    private UUID postId;
    private long revision;

    public UUID getPostId() {
        return postId;
    }

    public void setPostId(UUID postId) {
        this.postId = postId;
    }

    public long getRevision() {
        return revision;
    }

    public void setRevision(long revision) {
        this.revision = revision;
    }
}
