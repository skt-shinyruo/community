package com.nowcoder.community.content.application;

import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.repository.CommentContentRepository;
import com.nowcoder.community.content.domain.repository.PostContentBlockRepository;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import com.nowcoder.community.content.domain.repository.PostMediaAssetRepository;
import com.nowcoder.community.content.domain.repository.TagContentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(PostReadTransactionOperationsIntegrationTest.Config.class)
class PostReadTransactionOperationsIntegrationTest {

    @Autowired
    private PostReadTransactionOperations operations;

    @Autowired
    private PostContentRepository postContentRepository;

    @Autowired
    private PostContentBlockRepository postContentBlockRepository;

    @Autowired
    private TagContentRepository tagContentRepository;

    @Test
    void detailReadsRunInsideOneRepeatableReadSnapshot() {
        UUID postId = uuid(901);
        DiscussPost post = new DiscussPost();
        post.setId(postId);
        post.setAggregateVersion(12L);

        when(postContentRepository.getById(postId)).thenAnswer(ignored -> {
            assertRepeatableReadTransaction();
            return post;
        });
        when(postContentBlockRepository.listByPostId(postId)).thenAnswer(ignored -> {
            assertRepeatableReadTransaction();
            return List.of();
        });
        when(tagContentRepository.getTagsByPostIds(List.of(postId))).thenAnswer(ignored -> {
            assertRepeatableReadTransaction();
            return Map.of(postId, List.of("java"));
        });

        PostReadTransactionOperations.DetailSnapshot snapshot = operations.getDetail(postId);

        assertThat(snapshot.post().getAggregateVersion()).isEqualTo(12L);
        assertThat(snapshot.tags()).containsExactly("java");
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    private static void assertRepeatableReadTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
        assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
        assertThat(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())
                .isEqualTo(Connection.TRANSACTION_REPEATABLE_READ);
    }

    @EnableTransactionManagement
    static class Config {

        @Bean
        DataSource dataSource() {
            return new EmbeddedDatabaseBuilder()
                    .setType(EmbeddedDatabaseType.H2)
                    .generateUniqueName(true)
                    .build();
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        PostContentRepository postContentRepository() {
            return mock(PostContentRepository.class);
        }

        @Bean
        CommentContentRepository commentContentRepository() {
            return mock(CommentContentRepository.class);
        }

        @Bean
        TagContentRepository tagContentRepository() {
            return mock(TagContentRepository.class);
        }

        @Bean
        PostContentBlockRepository postContentBlockRepository() {
            return mock(PostContentBlockRepository.class);
        }

        @Bean
        PostMediaAssetRepository postMediaAssetRepository() {
            return mock(PostMediaAssetRepository.class);
        }

        @Bean
        PostReadTransactionOperations postReadTransactionOperations(
                PostContentRepository postContentRepository,
                CommentContentRepository commentContentRepository,
                TagContentRepository tagContentRepository,
                PostContentBlockRepository postContentBlockRepository,
                PostMediaAssetRepository postMediaAssetRepository
        ) {
            return new PostReadTransactionOperations(
                    postContentRepository,
                    commentContentRepository,
                    tagContentRepository,
                    postContentBlockRepository,
                    postMediaAssetRepository
            );
        }
    }
}
