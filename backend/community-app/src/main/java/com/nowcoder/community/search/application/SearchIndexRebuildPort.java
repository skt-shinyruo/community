package com.nowcoder.community.search.application;

import com.nowcoder.community.search.domain.model.PostSearchDocument;

import java.time.Duration;
import java.util.List;

/**
 * Owns the isolated index build and its publication as the active search read model.
 */
public interface SearchIndexRebuildPort {

    RebuildSession begin(Duration visibilityTtl);

    void cleanupRetiredIndices();

    interface RebuildSession extends AutoCloseable {

        void appendPage(List<PostSearchDocument> documents);

        boolean isValid();

        void publish();

        @Override
        void close();
    }
}
