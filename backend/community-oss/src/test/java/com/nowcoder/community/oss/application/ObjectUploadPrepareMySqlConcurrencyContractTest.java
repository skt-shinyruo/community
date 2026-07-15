package com.nowcoder.community.oss.application;

import com.nowcoder.community.oss.application.command.PrepareObjectUploadCommand;
import com.nowcoder.community.oss.application.result.ObjectUploadSessionResult;
import com.nowcoder.community.oss.domain.model.OssObject;
import com.nowcoder.community.oss.domain.model.OssObjectVersion;
import com.nowcoder.community.oss.domain.model.OssUploadSession;
import com.nowcoder.community.oss.domain.repository.OssObjectRepository;
import com.nowcoder.community.oss.domain.repository.OssObjectVersionRepository;
import com.nowcoder.community.oss.domain.repository.OssUploadSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = false)
@SpringBootTest(properties = {
        "spring.sql.init.mode=never",
        "security.jwt.hmac-secret=01234567890123456789012345678901",
        "security.jwt.issuer=community-oss-prepare-concurrency-test",
        "spring.cloud.discovery.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "oss.object-store.mode=local",
        "oss.object-store.local-root=${java.io.tmpdir}/community-oss-prepare-concurrency"
})
class ObjectUploadPrepareMySqlConcurrencyContractTest {

    private static final UUID REQUEST_ID = UUID.fromString("00000000-0000-7000-8000-000000007301");

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0")
            .withDatabaseName("community_oss")
            .withUsername("community_oss")
            .withPassword("communityosspass");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ObjectUploadApplicationService applicationService;

    @Autowired
    private OssUploadSessionRepository sessionRepository;

    @Autowired
    private OssObjectRepository objectRepository;

    @Autowired
    private OssObjectVersionRepository versionRepository;

    @Test
    void concurrentDifferentFingerprintsMustChooseOneWinnerWithoutCrossRowDrift() throws Exception {
        installCurrentSchema();
        PrepareObjectUploadCommand first = command("first.png", "asset-first", "actor-first");
        PrepareObjectUploadCommand second = command("second.png", "asset-second", "actor-second");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Attempt> firstAttempt = executor.submit(attempt("first", first, ready, start));
            Future<Attempt> secondAttempt = executor.submit(attempt("second", second, ready, start));
            assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Attempt> attempts = List.of(firstAttempt.get(), secondAttempt.get());
            assertThat(attempts).filteredOn(Attempt::succeeded).hasSize(1);
            List<Attempt> failures = attempts.stream().filter(attempt -> !attempt.succeeded()).toList();
            assertThat(failures).hasSize(1);
            assertThat(failures.get(0).failure())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("conflict");

            Attempt winner = attempts.stream().filter(Attempt::succeeded).findFirst().orElseThrow();
            PrepareObjectUploadCommand winningCommand = winner.name().equals("first") ? first : second;
            OssUploadSession session = sessionRepository.findByRequestId(REQUEST_ID).orElseThrow();
            OssObject object = objectRepository.findById(session.objectId()).orElseThrow();
            OssObjectVersion version = versionRepository.findById(session.versionId()).orElseThrow();

            assertThat(session.requestId()).isEqualTo(REQUEST_ID);
            assertThat(session.expectedFileName()).isEqualTo(winningCommand.fileName());
            assertThat(session.ownerId()).isEqualTo(winningCommand.ownerId());
            assertThat(session.createdBy()).isEqualTo(winningCommand.actorId());
            assertThat(object.ownerId()).isEqualTo(winningCommand.ownerId());
            assertThat(object.createdBy()).isEqualTo(winningCommand.actorId());
            assertThat(version.fileName()).isEqualTo(winningCommand.fileName());
            assertThat(version.objectId()).isEqualTo(object.objectId());
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private Callable<Attempt> attempt(
            String name,
            PrepareObjectUploadCommand command,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        return () -> {
            ready.countDown();
            if (!start.await(10, java.util.concurrent.TimeUnit.SECONDS)) {
                return new Attempt(name, null, new AssertionError("start barrier timed out"));
            }
            try {
                return new Attempt(name, applicationService.prepareUpload(command), null);
            } catch (Throwable failure) {
                return new Attempt(name, null, failure);
            }
        };
    }

    private PrepareObjectUploadCommand command(String fileName, String ownerId, String actorId) {
        return new PrepareObjectUploadCommand(
                REQUEST_ID,
                "CONTENT_POST_MEDIA",
                "community-app",
                "content",
                "post-media-draft",
                ownerId,
                "PUBLIC",
                fileName,
                "image/png",
                4L,
                "sha256-post",
                actorId
        );
    }

    private void installCurrentSchema() throws Exception {
        Path migrationDirectory = Path.of(
                "..",
                "community-oss-db-migrations",
                "src",
                "main",
                "resources",
                "db",
                "migration",
                "community-oss"
        );
        List<Path> migrations;
        try (var paths = Files.list(migrationDirectory)) {
            migrations = paths
                    .filter(path -> path.getFileName().toString().matches("V[0-9]+__.*\\.sql"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
        assertThat(migrations).isNotEmpty();
        try (Connection connection = dataSource.getConnection()) {
            for (Path migration : migrations) {
                ScriptUtils.executeSqlScript(
                        connection,
                        new EncodedResource(new FileSystemResource(migration), StandardCharsets.UTF_8)
                );
            }
        }
    }

    private record Attempt(String name, ObjectUploadSessionResult result, Throwable failure) {
        private boolean succeeded() {
            return result != null && failure == null;
        }
    }
}
