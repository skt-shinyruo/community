package com.nowcoder.community.drive.application;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.drive.application.DriveSpaceApplicationService.DriveSpaceResult;
import com.nowcoder.community.drive.application.port.DriveObjectStoragePort;
import com.nowcoder.community.drive.application.result.DriveEntryResult;
import com.nowcoder.community.drive.domain.model.DriveSpace;
import com.nowcoder.community.drive.domain.repository.DriveSpaceRepository;
import com.nowcoder.community.drive.infrastructure.persistence.MyBatisDriveEntryRepository;
import com.nowcoder.community.drive.infrastructure.persistence.MyBatisDriveSpaceRepository;
import com.nowcoder.community.drive.infrastructure.persistence.mapper.DriveEntryMapper;
import com.nowcoder.community.drive.infrastructure.persistence.mapper.DriveSpaceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@Testcontainers(disabledWithoutDocker = false)
@SpringBootTest(
        classes = DriveSpaceConcurrentCreateMySqlTest.TestApplication.class,
        properties = "spring.sql.init.mode=never"
)
class DriveSpaceConcurrentCreateMySqlTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000218");

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("community_drive_space")
            .withUsername("community")
            .withPassword("communitypass");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DriveSpaceApplicationService spaceApplicationService;

    @Autowired
    private DriveEntryApplicationService entryApplicationService;

    @Autowired
    private FirstEmptyReadBarrier firstEmptyReadBarrier;

    @BeforeEach
    void resetDriveTables() {
        jdbcTemplate.execute("drop table if exists drive_entry");
        jdbcTemplate.execute("drop table if exists drive_space");
        jdbcTemplate.execute("""
                create table drive_space (
                    space_id binary(16) not null,
                    user_id binary(16) not null,
                    quota_bytes bigint not null default 10737418240,
                    used_bytes bigint not null default 0,
                    reserved_bytes bigint not null default 0,
                    created_at timestamp not null default current_timestamp,
                    updated_at timestamp not null default current_timestamp,
                    primary key (space_id),
                    unique key uk_drive_space_user (user_id)
                ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci
                """);
        jdbcTemplate.execute("""
                create table drive_entry (
                    entry_id binary(16) not null,
                    space_id binary(16) not null,
                    parent_id binary(16) default null,
                    parent_key varchar(32) not null default '',
                    active_name varchar(255) default null,
                    type varchar(16) not null,
                    name varchar(255) not null,
                    object_id binary(16) default null,
                    version_id binary(16) default null,
                    size_bytes bigint not null default 0,
                    mime_type varchar(128) not null default '',
                    status varchar(16) not null,
                    trashed_at timestamp null default null,
                    delete_after timestamp null default null,
                    trash_root_id binary(16) default null,
                    created_at timestamp not null default current_timestamp,
                    updated_at timestamp not null default current_timestamp,
                    primary key (entry_id),
                    unique key uk_drive_entry_active_name (space_id, parent_key, active_name)
                ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci
                """);
        firstEmptyReadBarrier.reset();
    }

    @Test
    void concurrentFirstOpenShouldCreateOneSpaceAndListEmptyFiles() throws Exception {
        assertThat(jdbcTemplate.queryForObject("select @@transaction_isolation", String.class))
                .isEqualTo("REPEATABLE-READ");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<DriveSpaceResult> spaceAttempt = executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return spaceApplicationService.getSpace(USER_ID);
            });
            Future<List<DriveEntryResult>> listAttempt = executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return entryApplicationService.listEntries(USER_ID, null);
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            DriveSpaceResult space = await(spaceAttempt);
            List<DriveEntryResult> entries = await(listAttempt);

            assertThat(firstEmptyReadBarrier.bothInitialReadsObservedEmpty()).isTrue();
            assertThat(entries).isEmpty();
            assertThat(space.userId()).isEqualTo(USER_ID);
            assertThat(space.spaceId()).isNotNull();
            assertThat(countSpaces()).isEqualTo(1);

            assertThat(spaceApplicationService.getSpace(USER_ID).spaceId()).isEqualTo(space.spaceId());
            assertThat(entryApplicationService.listEntries(USER_ID, null)).isEmpty();
            assertThat(countSpaces()).isEqualTo(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private int countSpaces() {
        Integer count = jdbcTemplate.queryForObject("select count(*) from drive_space", Integer.class);
        return count == null ? 0 : count;
    }

    private static <T> T await(Future<T> attempt) throws Exception {
        try {
            return attempt.get(20, TimeUnit.SECONDS);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception failure) {
                throw failure;
            }
            throw exception;
        }
    }

    /**
     * Holds the first two empty space reads until both transactions have taken their
     * repeatable-read snapshot. Without this, one request can commit before the other
     * starts and the duplicate-key path never runs.
     */
    static final class FirstEmptyReadBarrier {

        private final AtomicInteger emptyReads = new AtomicInteger();
        private volatile CountDownLatch arrived = new CountDownLatch(2);

        void reset() {
            emptyReads.set(0);
            arrived = new CountDownLatch(2);
        }

        void afterEmptyRead() {
            int observed = emptyReads.incrementAndGet();
            if (observed > 2) {
                return;
            }
            arrived.countDown();
            try {
                if (!arrived.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "timed out waiting for both first reads to observe an empty drive space"
                    );
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while waiting for the other drive space read", exception);
            }
        }

        boolean bothInitialReadsObservedEmpty() {
            return emptyReads.get() >= 2 && arrived.getCount() == 0;
        }
    }

    static final class EmptyReadBarrierDriveSpaceRepository implements DriveSpaceRepository {

        private final DriveSpaceRepository delegate;
        private final FirstEmptyReadBarrier barrier;

        EmptyReadBarrierDriveSpaceRepository(DriveSpaceRepository delegate, FirstEmptyReadBarrier barrier) {
            this.delegate = delegate;
            this.barrier = barrier;
        }

        @Override
        public Optional<DriveSpace> findByUserId(UUID userId) {
            Optional<DriveSpace> found = delegate.findByUserId(userId);
            if (found.isEmpty()) {
                barrier.afterEmptyRead();
            }
            return found;
        }

        @Override
        public Optional<DriveSpace> findById(UUID spaceId) {
            return delegate.findById(spaceId);
        }

        @Override
        public DriveSpace lockById(UUID spaceId) {
            return delegate.lockById(spaceId);
        }

        @Override
        public boolean reserve(UUID spaceId, long bytes, Instant updatedAt) {
            return delegate.reserve(spaceId, bytes, updatedAt);
        }

        @Override
        public boolean commitReserved(UUID spaceId, long bytes, Instant updatedAt) {
            return delegate.commitReserved(spaceId, bytes, updatedAt);
        }

        @Override
        public boolean releaseReserved(UUID spaceId, long bytes, Instant updatedAt) {
            return delegate.releaseReserved(spaceId, bytes, updatedAt);
        }

        @Override
        public CreateResult create(DriveSpace space) {
            return delegate.create(space);
        }

        @Override
        public void save(DriveSpace space) {
            delegate.save(space);
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan(basePackageClasses = {DriveSpaceMapper.class, DriveEntryMapper.class})
    @Import({
            DriveSpaceApplicationService.class,
            DriveEntryApplicationService.class,
            DriveTransactionOperations.class,
            MyBatisDriveEntryRepository.class
    })
    static class TestApplication {

        @Bean
        FirstEmptyReadBarrier firstEmptyReadBarrier() {
            return new FirstEmptyReadBarrier();
        }

        @Bean
        DriveSpaceRepository driveSpaceRepository(DriveSpaceMapper mapper, FirstEmptyReadBarrier barrier) {
            return new EmptyReadBarrierDriveSpaceRepository(new MyBatisDriveSpaceRepository(mapper), barrier);
        }

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        UuidV7Generator uuidV7Generator(Clock clock) {
            return new UuidV7Generator(clock);
        }

        @Bean
        DriveObjectStoragePort driveObjectStoragePort() {
            return mock(DriveObjectStoragePort.class);
        }
    }
}
