package com.nowcoder.community.user.application;

import com.nowcoder.community.user.application.command.CreateAvatarUploadSessionCommand;
import com.nowcoder.community.user.application.port.AvatarStoragePort;
import com.nowcoder.community.user.application.result.AvatarUploadSessionResult;
import com.nowcoder.community.user.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

@SpringJUnitConfig(UserAvatarTransactionBoundaryIntegrationTest.Config.class)
class UserAvatarTransactionBoundaryIntegrationTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000007");
    private static final UUID OBJECT_ID = UUID.fromString("00000000-0000-7000-8000-000000000030");

    @Autowired
    private UserAvatarApplicationService service;

    @Autowired
    private TransactionProbe probe;

    @Test
    void storageCallsShouldRunWithoutTransactionAndRepositoryWriteShouldRunInsideOne() {
        service.createUploadSession(
                USER_ID,
                USER_ID,
                new CreateAvatarUploadSessionCommand("avatar.png", "image/png", 6L, "sha256-avatar")
        );
        service.updateAvatar(USER_ID, USER_ID, OBJECT_ID);

        assertThat(probe.storageTransactionStates).containsExactly(false, false);
        assertThat(probe.repositoryTransactionStates).containsExactly(true);
    }

    @EnableTransactionManagement
    static class Config {

        @Bean(destroyMethod = "shutdown")
        EmbeddedDatabase dataSource() {
            return new EmbeddedDatabaseBuilder()
                    .generateUniqueName(true)
                    .setType(EmbeddedDatabaseType.H2)
                    .build();
        }

        @Bean
        PlatformTransactionManager transactionManager(EmbeddedDatabase dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        TransactionProbe transactionProbe() {
            return new TransactionProbe();
        }

        @Bean
        AvatarStoragePort avatarStoragePort(TransactionProbe probe) {
            return new AvatarStoragePort() {
                @Override
                public AvatarUploadSessionResult createUploadSession(
                        UUID userId,
                        CreateAvatarUploadSessionCommand command
                ) {
                    probe.storageTransactionStates.add(TransactionSynchronizationManager.isActualTransactionActive());
                    return new AvatarUploadSessionResult(
                            "upload-id",
                            OBJECT_ID,
                            UUID.fromString("00000000-0000-7000-8000-000000000031"),
                            "/upload",
                            "POST",
                            "file",
                            Map.of(),
                            Map.of(),
                            1024L,
                            List.of("image/png"),
                            Instant.parse("2026-08-02T09:00:00Z")
                    );
                }

                @Override
                public String resolvePublicAvatarUrl(UUID userId, UUID objectId) {
                    probe.storageTransactionStates.add(TransactionSynchronizationManager.isActualTransactionActive());
                    return "https://cdn.example.com/avatar.png";
                }
            };
        }

        @Bean
        UserRepository userRepository(TransactionProbe probe) {
            UserRepository repository = mock(UserRepository.class);
            doAnswer(invocation -> {
                probe.repositoryTransactionStates.add(
                        TransactionSynchronizationManager.isActualTransactionActive()
                );
                return null;
            }).when(repository).updateHeaderUrl(any(UUID.class), anyString());
            return repository;
        }

        @Bean
        UserAvatarTransactionOperations userAvatarTransactionOperations(UserRepository userRepository) {
            return new UserAvatarTransactionOperations(userRepository);
        }

        @Bean
        UserAvatarApplicationService userAvatarApplicationService(
                AvatarStoragePort avatarStoragePort,
                UserAvatarTransactionOperations transactionOperations
        ) {
            return new UserAvatarApplicationService(avatarStoragePort, transactionOperations);
        }
    }

    static class TransactionProbe {
        private final List<Boolean> storageTransactionStates = new ArrayList<>();
        private final List<Boolean> repositoryTransactionStates = new ArrayList<>();
    }
}
