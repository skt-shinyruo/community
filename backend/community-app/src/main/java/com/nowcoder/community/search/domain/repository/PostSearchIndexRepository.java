package com.nowcoder.community.search.domain.repository;

public interface PostSearchIndexRepository {

    void ensureAliasReady();

    String createNewIndex();

    void switchAliasTo(String newIndex);

    void cleanupOldIndices();
}
