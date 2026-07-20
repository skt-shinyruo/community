package com.nowcoder.community.social.infrastructure.persistence;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.social.domain.model.LikeRelation;
import com.nowcoder.community.social.domain.repository.LikeRepository;
import com.nowcoder.community.social.infrastructure.persistence.dataobject.LikeScanDataObject;
import com.nowcoder.community.social.infrastructure.persistence.mapper.LikeMapper;
import org.apache.ibatis.annotations.Delete;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.common.constants.EntityTypes.POST;
import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class MyBatisLikeRepositoryTest {

    private static final UUID ENTITY_ID = uuid(710);
    private static final UUID OWNER_ID = uuid(711);
    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    @Autowired
    private LikeRepository repository;

    @Autowired
    private LikeMapper mapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from social_like");
        jdbcTemplate.update("delete from social_user_like_count");
    }

    @Test
    void addFindAndScanShouldRoundTripCompleteRelation() {
        LikeRelation first = relation(uuid(720), uuid(1));
        LikeRelation second = relation(uuid(721), uuid(2));

        assertThat(repository.addLike(first)).isTrue();
        assertThat(repository.addLike(second)).isTrue();
        assertThat(repository.addLike(new LikeRelation(uuid(722), uuid(1), POST, ENTITY_ID, OWNER_ID)))
                .isFalse();

        assertThat(repository.findLike(uuid(1), POST, ENTITY_ID)).contains(first);
        assertThat(repository.scanLikesByEntity(POST, ENTITY_ID, ZERO_UUID, 10))
                .containsExactly(first, second);
        assertThat(storedRelationInstance(uuid(1))).isEqualTo(first.relationInstanceId());

        List<LikeScanDataObject> rows = mapper.scanLikes(POST, ZERO_UUID, ZERO_UUID, 10);
        assertThat(rows)
                .extracting(LikeScanDataObject::getRelationInstanceId)
                .containsExactly(first.relationInstanceId(), second.relationInstanceId());
    }

    @Test
    void removeShouldCompareStableKeyAndExpectedInstance() {
        LikeRelation first = relation(uuid(730), uuid(1));
        LikeRelation wrongInstance = new LikeRelation(uuid(731), uuid(1), POST, ENTITY_ID, OWNER_ID);
        LikeRelation second = relation(uuid(732), uuid(1));
        assertThat(repository.addLike(first)).isTrue();

        assertThat(repository.removeLike(wrongInstance)).isFalse();
        assertThat(repository.findLike(uuid(1), POST, ENTITY_ID)).contains(first);
        assertThat(repository.removeLike(first)).isTrue();

        assertThat(repository.addLike(second)).isTrue();
        assertThat(repository.removeLike(first)).isFalse();
        assertThat(repository.findLike(uuid(1), POST, ENTITY_ID)).contains(second);
        assertThat(repository.removeLike(second)).isTrue();
    }

    @Test
    void deleteSqlShouldGuardStableRelationKeyAndExpectedInstance() throws Exception {
        Method method = LikeMapper.class.getMethod(
                "deleteLike",
                UUID.class,
                int.class,
                UUID.class,
                UUID.class
        );

        String sql = String.join(" ", method.getAnnotation(Delete.class).value())
                .replaceAll("\\s+", " ")
                .toLowerCase();

        assertThat(sql).contains("user_id = #{userid, jdbctype=binary}");
        assertThat(sql).contains("entity_type = #{entitytype}");
        assertThat(sql).contains("entity_id = #{entityid, jdbctype=binary}");
        assertThat(sql).contains("relation_instance_id = #{relationinstanceid, jdbctype=binary}");
    }

    private LikeRelation relation(UUID relationInstanceId, UUID actorUserId) {
        return new LikeRelation(relationInstanceId, actorUserId, POST, ENTITY_ID, OWNER_ID);
    }

    private UUID storedRelationInstance(UUID actorUserId) {
        byte[] value = jdbcTemplate.queryForObject(
                "select relation_instance_id from social_like where user_id = ? and entity_type = ? and entity_id = ?",
                byte[].class,
                BinaryUuidCodec.toBytes(actorUserId),
                POST,
                BinaryUuidCodec.toBytes(ENTITY_ID)
        );
        return BinaryUuidCodec.fromBytes(value);
    }
}
