package com.nowcoder.community.user.infrastructure.persistence;

import com.nowcoder.community.user.domain.model.UserAccount;
import com.nowcoder.community.user.domain.repository.UserRepository;
import com.nowcoder.community.user.infrastructure.persistence.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MyBatisUserRepositoryInsertOutcomeTest {

    private static final UUID USER_ID = UUID.fromString("018f7f66-3f8f-7a5a-8e32-000000000041");

    @Test
    void successfulInsertShouldReturnCreated() throws ReflectiveOperationException {
        UserMapper mapper = mock(UserMapper.class);
        when(mapper.insertUser(any())).thenReturn(1);

        Object outcome = invokeInsert(new MyBatisUserRepository(mapper));

        assertThat(outcome).hasToString("CREATED");
    }

    @Test
    void duplicateKeyShouldReturnAlreadyExists() throws ReflectiveOperationException {
        UserMapper mapper = mock(UserMapper.class);
        when(mapper.insertUser(any())).thenThrow(new DuplicateKeyException("duplicate user"));

        Object outcome = invokeInsert(new MyBatisUserRepository(mapper));

        assertThat(outcome).hasToString("ALREADY_EXISTS");
    }

    @Test
    void unknownIntegrityViolationShouldReturnConflict() throws ReflectiveOperationException {
        UserMapper mapper = mock(UserMapper.class);
        when(mapper.insertUser(any())).thenThrow(new DataIntegrityViolationException("unknown integrity failure"));

        Object outcome = invokeInsert(new MyBatisUserRepository(mapper));

        assertThat(outcome).hasToString("CONFLICT");
    }

    private Object invokeInsert(UserRepository repository) throws ReflectiveOperationException {
        Method insertUser = UserRepository.class.getMethod("insertUser", UserAccount.class);
        return insertUser.invoke(repository, user());
    }

    private UserAccount user() {
        return new UserAccount(
                USER_ID,
                "alice",
                "$2a$10$7EqJtq98hPqEX7fNZaFWoOHiE9VYh4Vh7H1w52x1x7YjQwlhbR1XK",
                "",
                "alice@example.com",
                0,
                1,
                "header",
                Date.from(Instant.parse("2026-04-28T01:00:00Z")),
                null,
                null,
                0L,
                0L
        );
    }
}
