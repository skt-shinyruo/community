package com.nowcoder.community.content.application;

import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.common.outbox.JdbcOutboxEventStore;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import com.nowcoder.community.content.infrastructure.event.JacksonContentContractEventCodec;
import com.nowcoder.community.content.infrastructure.event.OutboxContentEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(PostScoreOutboxAtomicityIntegrationTest.Config.class)
class PostScoreOutboxAtomicityIntegrationTest {

    private static final UUID POST_ID = uuid(930);

    @Autowired
    private PostHotFeedProjectionTransactionOperations transactionOperations;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpSchema() {
        jdbcTemplate.execute("drop table if exists outbox_event");
        jdbcTemplate.execute("drop table if exists post_score_state");
        jdbcTemplate.execute("""
                create table post_score_state (
                    post_id varchar(36) primary key,
                    aggregate_version bigint not null,
                    score_version bigint not null,
                    score double precision not null
                )
                """);
        jdbcTemplate.execute("""
                create table outbox_event (
                    id binary(16) primary key,
                    event_id varchar(255) not null unique,
                    topic varchar(4) not null,
                    event_key varchar(255) not null,
                    payload clob not null,
                    status varchar(32) not null,
                    lease_token binary(16),
                    processing_lease_until timestamp,
                    retry_count int not null,
                    next_retry_at timestamp,
                    last_error varchar(1000),
                    trace_id varchar(64),
                    traceparent varchar(255),
                    created_at timestamp default current_timestamp,
                    updated_at timestamp default current_timestamp
                )
                """);
        jdbcTemplate.update(
                "insert into post_score_state(post_id, aggregate_version, score_version, score) values (?, ?, ?, ?)",
                POST_ID.toString(),
                7L,
                2L,
                10.0
        );
    }

    @Test
    void outboxInsertFailureShouldRollBackScoreAndScoreVersion() {
        assertThatThrownBy(() -> transactionOperations.updateScore(POST_ID, 51.0, 7L))
                .isInstanceOf(RuntimeException.class);

        assertThat(jdbcTemplate.queryForObject(
                "select score from post_score_state where post_id = ?",
                Double.class,
                POST_ID.toString()
        )).isEqualTo(10.0);
        assertThat(jdbcTemplate.queryForObject(
                "select score_version from post_score_state where post_id = ?",
                Long.class,
                POST_ID.toString()
        )).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject("select count(*) from outbox_event", Long.class)).isZero();
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
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        PostContentRepository postContentRepository(JdbcTemplate jdbcTemplate) {
            PostContentRepository repository = mock(PostContentRepository.class);
            when(repository.updateScore(any(UUID.class), anyDouble(), anyLong())).thenAnswer(invocation -> {
                UUID postId = invocation.getArgument(0);
                double score = invocation.getArgument(1);
                long aggregateVersion = invocation.getArgument(2);
                int updated = jdbcTemplate.update(
                        "update post_score_state set score = ?, score_version = score_version + 1 "
                                + "where post_id = ? and aggregate_version = ?",
                        score,
                        postId.toString(),
                        aggregateVersion
                );
                if (updated != 1) {
                    throw new IllegalStateException("score CAS failed");
                }
                return jdbcTemplate.queryForObject(
                        "select score_version from post_score_state where post_id = ?",
                        Long.class,
                        postId.toString()
                );
            });
            return repository;
        }

        @Bean
        ContentEventPublisher contentEventPublisher(JdbcTemplate jdbcTemplate) {
            return new OutboxContentEventPublisher(
                    new JacksonContentContractEventCodec(
                            new JacksonJsonCodec(JsonMappers.standard())
                    ),
                    new JdbcOutboxEventStore(jdbcTemplate),
                    "content.events"
            );
        }

        @Bean
        PostHotFeedProjectionTransactionOperations transactionOperations(
                PostContentRepository postContentRepository,
                ContentEventPublisher contentEventPublisher
        ) {
            return new PostHotFeedProjectionTransactionOperations(postContentRepository, contentEventPublisher);
        }
    }
}
