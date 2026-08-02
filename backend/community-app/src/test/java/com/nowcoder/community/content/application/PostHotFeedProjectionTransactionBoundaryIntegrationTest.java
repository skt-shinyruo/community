package com.nowcoder.community.content.application;

import com.nowcoder.community.content.application.command.ProjectPostHotFeedCommand;
import com.nowcoder.community.content.contracts.event.PostScorePayload;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import com.nowcoder.community.content.domain.service.PostHotnessDomainService;
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
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(PostHotFeedProjectionTransactionBoundaryIntegrationTest.Config.class)
class PostHotFeedProjectionTransactionBoundaryIntegrationTest {

    @Autowired
    private PostHotFeedProjectionApplicationService applicationService;

    @Autowired
    private PostContentRepository postContentRepository;

    @Autowired
    private LikeQueryPort likeQueryPort;

    @Autowired
    private PostFeedCache postFeedCache;

    @Autowired
    private PostSummaryCache postSummaryCache;

    @Autowired
    private PostDetailCache postDetailCache;

    @Autowired
    private PostCounterCache postCounterCache;

    @Autowired
    private ContentEventPublisher contentEventPublisher;

    @Test
    void onlyScoreCasRunsInsideJdbcTransaction() {
        UUID postId = uuid(920);
        UUID boardId = uuid(921);
        DiscussPost post = post(postId, boardId);

        when(postContentRepository.getByIdAllowDeleted(postId)).thenAnswer(ignored -> {
            assertOutsideTransaction();
            return post;
        });
        when(likeQueryPort.countPostLikes(postId)).thenAnswer(ignored -> {
            assertOutsideTransaction();
            return 9L;
        });
        when(postContentRepository.updateScore(postId, 51.0, 7L)).thenAnswer(ignored -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return 3L;
        });
        doAnswer(ignored -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return null;
        }).when(contentEventPublisher).publishPostScoreUpdated(any(PostScorePayload.class));
        assertCacheCallsOutsideTransaction();

        applicationService.project(new ProjectPostHotFeedCommand(
                postId,
                boardId,
                1.5,
                "evt-transaction-boundary",
                7L,
                false
        ));

        verify(postContentRepository).updateScore(postId, 51.0, 7L);
        verify(contentEventPublisher).publishPostScoreUpdated(new PostScorePayload(postId, 7L, 3L, 51.0));
        verify(postFeedCache).upsertGlobalHot(postId, 51.0, "hot-v2", 7L, 3L);
        assertOutsideTransaction();
    }

    private void assertCacheCallsOutsideTransaction() {
        doAnswer(ignored -> {
            assertOutsideTransaction();
            return null;
        }).when(postCounterCache).updateScore(any(), anyDouble());
        doAnswer(ignored -> {
            assertOutsideTransaction();
            return null;
        }).when(postFeedCache).writeRankVersion(any());
        doAnswer(ignored -> {
            assertOutsideTransaction();
            return null;
        }).when(postFeedCache).remove(any(), any(), anyLong());
        doAnswer(ignored -> {
            assertOutsideTransaction();
            return null;
        }).when(postFeedCache).upsertGlobalHot(any(), anyDouble(), any(), anyLong(), anyLong());
        doAnswer(ignored -> {
            assertOutsideTransaction();
            return null;
        }).when(postFeedCache).upsertBoardHot(any(), any(), anyDouble(), any(), anyLong(), anyLong());
        doAnswer(ignored -> {
            assertOutsideTransaction();
            return null;
        }).when(postSummaryCache).evictAll(any(List.class), anyLong());
        doAnswer(ignored -> {
            assertOutsideTransaction();
            return null;
        }).when(postDetailCache).evict(any(), anyLong());
    }

    private static void assertOutsideTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    private static DiscussPost post(UUID postId, UUID boardId) {
        DiscussPost post = new DiscussPost();
        post.setId(postId);
        post.setCategoryId(boardId);
        post.setStatus(1);
        post.setScore(42.0);
        post.setCommentCount(6);
        post.setCreateTime(new Date());
        post.setAggregateVersion(7L);
        return post;
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
        LikeQueryPort likeQueryPort() {
            return mock(LikeQueryPort.class);
        }

        @Bean
        PostFeedCache postFeedCache() {
            return mock(PostFeedCache.class);
        }

        @Bean
        PostSummaryCache postSummaryCache() {
            return mock(PostSummaryCache.class);
        }

        @Bean
        PostDetailCache postDetailCache() {
            return mock(PostDetailCache.class);
        }

        @Bean
        PostCounterCache postCounterCache() {
            return mock(PostCounterCache.class);
        }

        @Bean
        ContentEventPublisher contentEventPublisher() {
            return mock(ContentEventPublisher.class);
        }

        @Bean
        PostHotnessDomainService postHotnessDomainService() {
            PostHotnessDomainService service = mock(PostHotnessDomainService.class);
            when(service.recomputeScore(any(), anyLong())).thenAnswer(ignored -> {
                assertOutsideTransaction();
                return 51.0;
            });
            return service;
        }

        @Bean
        ContentFeedPolicyProperties contentFeedPolicyProperties() {
            ContentFeedPolicyProperties properties = new ContentFeedPolicyProperties();
            properties.setHotRankVersion("hot-v2");
            return properties;
        }

        @Bean
        HotFeedProjectionGuard hotFeedProjectionGuard() {
            HotFeedProjectionGuard guard = mock(HotFeedProjectionGuard.class);
            HotFeedProjectionGuard.ProjectionAttempt attempt = HotFeedProjectionGuard.ProjectionAttempt.accepted(
                    uuid(920),
                    "evt-transaction-boundary",
                    7L,
                    PostProjectionVersionLane.POST,
                    false,
                    "runtime-boundary"
            );
            when(guard.tryBegin(uuid(920), "evt-transaction-boundary", 7L, PostProjectionVersionLane.POST, false))
                    .thenAnswer(ignored -> {
                        assertOutsideTransaction();
                        return attempt;
                    });
            when(guard.isCurrent(attempt)).thenAnswer(ignored -> {
                assertOutsideTransaction();
                return true;
            });
            doAnswer(ignored -> {
                assertOutsideTransaction();
                return null;
            }).when(guard).commit(attempt);
            return guard;
        }

        @Bean
        HotFeedProjectionCompletion hotFeedProjectionCompletion() {
            return (committedAction, rolledBackAction) -> {
                assertOutsideTransaction();
                committedAction.run();
            };
        }

        @Bean
        PostHotFeedProjectionTransactionOperations postHotFeedProjectionTransactionOperations(
                PostContentRepository postContentRepository,
                ContentEventPublisher contentEventPublisher
        ) {
            return new PostHotFeedProjectionTransactionOperations(postContentRepository, contentEventPublisher);
        }

        @Bean
        PostHotFeedProjectionApplicationService postHotFeedProjectionApplicationService(
                PostContentRepository postContentRepository,
                LikeQueryPort likeQueryPort,
                PostFeedCache postFeedCache,
                PostSummaryCache postSummaryCache,
                PostDetailCache postDetailCache,
                PostCounterCache postCounterCache,
                PostHotnessDomainService postHotnessDomainService,
                ContentFeedPolicyProperties policyProperties,
                HotFeedProjectionGuard projectionGuard,
                PostHotFeedProjectionTransactionOperations transactionOperations,
                HotFeedProjectionCompletion projectionCompletion
        ) {
            return new PostHotFeedProjectionApplicationService(
                    postContentRepository,
                    likeQueryPort,
                    postFeedCache,
                    postSummaryCache,
                    postDetailCache,
                    postCounterCache,
                    postHotnessDomainService,
                    policyProperties,
                    projectionGuard,
                    transactionOperations,
                    projectionCompletion
            );
        }
    }
}
