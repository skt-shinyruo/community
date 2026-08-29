package com.nowcoder.community.user.infrastructure.persistence;

import com.nowcoder.community.user.application.UserCredentialApplicationService;
import com.nowcoder.community.user.application.port.UsernameAuthenticationSubjectPort;
import com.nowcoder.community.user.api.model.UserAuthenticationResultView;
import com.nowcoder.community.user.domain.service.PasswordPolicyDomainService;
import com.nowcoder.community.user.domain.service.UserCredentialDomainService;
import com.nowcoder.community.user.domain.service.UsernamePolicyDomainService;
import com.nowcoder.community.user.infrastructure.persistence.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = MyBatisUsernameAuthenticationSubjectMySqlContractTest.TestApplication.class,
        properties = {
                "spring.sql.init.mode=never",
                "mybatis.mapper-locations=classpath:mapper/user_mapper.xml"
        }
)
@Testcontainers
class MyBatisUsernameAuthenticationSubjectMySqlContractTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("community_subject_contract")
            .withUsername("community")
            .withPassword("communitypass")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");

    @Autowired
    private UsernameAuthenticationSubjectPort subjectPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserMapper userMapper;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @BeforeEach
    void resetUserTable() {
        jdbcTemplate.execute("drop table if exists user");
        jdbcTemplate.execute("""
                create table user (
                    id binary(16) not null,
                    username varchar(255) collate utf8mb4_unicode_ci not null,
                    password varchar(255) collate utf8mb4_unicode_ci default null,
                    salt varchar(255) collate utf8mb4_unicode_ci default null,
                    email varchar(255) collate utf8mb4_unicode_ci default null,
                    type int not null default 0,
                    status int default 0,
                    header_url varchar(255) collate utf8mb4_unicode_ci default null,
                    create_time timestamp null default current_timestamp,
                    mute_until timestamp null default null,
                    ban_until timestamp null default null,
                    policy_version bigint not null default 0,
                    security_version bigint not null default 0,
                    primary key (id),
                    unique key uk_user_username (username)
                ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci
                """);
    }

    @Test
    void collationEquivalentUsernamesShouldResolveToTheSameSubject() {
        List<UsernamePair> equivalentPairs = List.of(
                new UsernamePair("Jose", "JOS\u00C9"),
                new UsernamePair("\u306F", "\u30CF"),
                new UsernamePair("\u3083", "\u3084"),
                new UsernamePair("a\u00A0", "a"),
                new UsernamePair("\u00F8la", "\u01FFla"),
                new UsernamePair("\u00DF", "ss")
        );

        for (UsernamePair pair : equivalentPairs) {
            assertThat(subjectPort.resolve(pair.left()))
                    .as("%s and %s", pair.left(), pair.right())
                    .isEqualTo(subjectPort.resolve(pair.right()));
        }
    }

    @Test
    void collationDistinctUsernamesShouldResolveToDifferentSubjects() {
        List<UsernamePair> distinctPairs = List.of(
                new UsernamePair("\u00E6", "ae"),
                new UsernamePair("\u00FE", "th"),
                new UsernamePair("\u0131", "i"),
                new UsernamePair("alice", "a\u0142ice")
        );

        for (UsernamePair pair : distinctPairs) {
            assertThat(subjectPort.resolve(pair.left()))
                    .as("%s and %s", pair.left(), pair.right())
                    .isNotEqualTo(subjectPort.resolve(pair.right()));
        }
    }

    @Test
    void subjectShouldBeOpaqueNonblankAndIndependentOfAccountExistence() {
        String beforeInsert = subjectPort.resolve("JOS\u00C9");

        jdbcTemplate.update(
                "insert into user(id, username) values (unhex('00000000000070008000000000000001'), ?)",
                "Jose"
        );

        assertThat(subjectPort.resolve("JOS\u00C9")).isEqualTo(beforeInsert);
        assertThat(beforeInsert)
                .matches("utf8mb4_unicode_ci:v1:[0-9a-f]{64}")
                .doesNotContain("Jose", "JOS\u00C9");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from user where username = ?",
                Integer.class,
                "JOS\u00C9"
        )).isEqualTo(1);
    }

    @Test
    void safeAliasMustNotAuthenticateAnUnsafeStoredUsername() {
        String storedUsername = "a\u200Dlice";
        jdbcTemplate.update(
                """
                        insert into user(id, username, password, status)
                        values (unhex('00000000000070008000000000000002'), ?, ?, 1)
                        """,
                storedUsername,
                new BCryptPasswordEncoder().encode("secret12")
        );
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from user where username = ?",
                Integer.class,
                "alice"
        )).isEqualTo(1);

        UsernamePolicyDomainService usernamePolicy = new UsernamePolicyDomainService();
        UserCredentialApplicationService credentialService = new UserCredentialApplicationService(
                new MyBatisUserRepository(userMapper),
                new UserCredentialDomainService(usernamePolicy),
                new PasswordPolicyDomainService(),
                subjectPort,
                java.time.Clock.systemUTC()
        );

        UserCredentialApplicationService.PreparedAuthentication preparation =
                credentialService.prepare("alice");

        assertThat(preparation.user()).isNull();
        assertThat(preparation.storedHashUsable()).isFalse();
        assertThat(credentialService.authenticate(preparation, "secret12").failure())
                .isEqualTo(UserAuthenticationResultView.Failure.INVALID_CREDENTIALS);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan(basePackageClasses = UserMapper.class)
    @Import(MyBatisUsernameAuthenticationSubjectAdapter.class)
    static class TestApplication {
    }

    private record UsernamePair(String left, String right) {
    }
}
