package com.nowcoder.community.user.infrastructure.persistence;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.user.infrastructure.persistence.dataobject.UserDataObject;
import com.nowcoder.community.user.infrastructure.persistence.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class UserMapperPersistenceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000007");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserMapper userMapper;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from auth_refresh_token");
        jdbcTemplate.update("delete from user");
    }

    @Test
    void insertUserShouldPersistApplicationAssignedUuidPrimaryKey() {
        UserDataObject user = new UserDataObject();
        user.setId(USER_ID);
        user.setUsername("alice");
        user.setPassword("encoded-password");
        user.setSalt("");
        user.setEmail("alice@example.com");
        user.setType(1);
        user.setStatus(0);
        user.setHeaderUrl("http://example.com/avatar.png");
        user.setCreateTime(new Date());

        int inserted = userMapper.insertUser(user);

        assertThat(inserted).isEqualTo(1);
        assertThat(user.getId()).isEqualTo(USER_ID);

        byte[] storedId = jdbcTemplate.queryForObject(
                "select id from user where username = ?",
                (rs, rowNum) -> rs.getBytes(1),
                "alice"
        );
        assertThat(storedId).hasSize(16);
        assertThat(BinaryUuidCodec.fromBytes(storedId)).isEqualTo(USER_ID);

        UserDataObject persisted = userMapper.selectById(USER_ID);
        assertThat(persisted).isNotNull();
        assertThat(persisted.getId()).isEqualTo(USER_ID);
        assertThat(persisted.getUsername()).isEqualTo("alice");
    }
}
