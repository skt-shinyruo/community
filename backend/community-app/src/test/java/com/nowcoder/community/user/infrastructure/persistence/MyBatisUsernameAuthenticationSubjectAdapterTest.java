package com.nowcoder.community.user.infrastructure.persistence;

import com.nowcoder.community.user.infrastructure.persistence.mapper.UserMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MyBatisUsernameAuthenticationSubjectAdapterTest {

    private static final String DIGEST = "307c4665ce6e7d87ab0e3cca86bc33d46cfdd264748b973a789b18f94299bd7f";

    @Test
    void resolveShouldNamespaceTheDatabaseCollationDigest() {
        UserMapper mapper = mock(UserMapper.class);
        when(mapper.selectUsernameAuthenticationSubjectDigest("alice")).thenReturn(DIGEST);
        MyBatisUsernameAuthenticationSubjectAdapter adapter =
                new MyBatisUsernameAuthenticationSubjectAdapter(mapper);

        String subject = adapter.resolve("alice");

        assertThat(subject).isEqualTo("utf8mb4_unicode_ci:v1:" + DIGEST);
    }

    @Test
    void resolveShouldFailClosedWhenTheDatabaseReturnsNoDigest() {
        UserMapper mapper = mock(UserMapper.class);
        when(mapper.selectUsernameAuthenticationSubjectDigest("alice")).thenReturn(null);
        MyBatisUsernameAuthenticationSubjectAdapter adapter =
                new MyBatisUsernameAuthenticationSubjectAdapter(mapper);

        assertThatThrownBy(() -> adapter.resolve("alice"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("username authentication subject");
    }
}
