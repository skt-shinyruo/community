package com.nowcoder.community.content.infrastructure.persistence.mapper;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.content.infrastructure.persistence.dataobject.PostContentBlockDataObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = CommunityAppApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class PostContentBlockMapperPersistenceTest {

    private static final UUID POST_ID = UUID.fromString("00000000-0000-7000-8000-000000000601");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000602");
    private static final UUID BLOCK_ID = UUID.fromString("00000000-0000-7000-8000-000000000603");
    private static final UUID MEDIA_ID = UUID.fromString("00000000-0000-7000-8000-000000000604");

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PostContentBlockMapper mapper;

    @MockBean
    ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from post_content_block");
        jdbcTemplate.update("delete from post_media_asset");
        jdbcTemplate.update("delete from discuss_post");
    }

    @Test
    void insertAndListByPostIdShouldPreserveOrderAndMediaReference() {
        jdbcTemplate.update(
                "insert into discuss_post(id, user_id, title, type, status, create_time, comment_count, score) values (?, ?, ?, ?, ?, ?, ?, ?)",
                BinaryUuidCodec.toBytes(POST_ID),
                BinaryUuidCodec.toBytes(USER_ID),
                "title",
                0,
                0,
                Timestamp.from(Instant.parse("2026-05-09T00:00:00Z")),
                0,
                0.0
        );

        PostContentBlockDataObject row = new PostContentBlockDataObject();
        row.setId(BLOCK_ID);
        row.setPostId(POST_ID);
        row.setBlockIndex(0);
        row.setBlockType("image");
        row.setTextContent("");
        row.setMediaAssetId(MEDIA_ID);
        row.setCaption("chart");
        row.setDisplayName("");
        row.setLanguage("");
        row.setMetadataJson("{\"layout\":\"wide\"}");
        row.setCreateTime(Timestamp.from(Instant.parse("2026-05-09T00:01:00Z")));

        assertThat(mapper.insert(row)).isEqualTo(1);

        List<PostContentBlockDataObject> rows = mapper.selectByPostId(POST_ID);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getId()).isEqualTo(BLOCK_ID);
        assertThat(rows.get(0).getMediaAssetId()).isEqualTo(MEDIA_ID);
        assertThat(rows.get(0).getCaption()).isEqualTo("chart");
        assertThat(rows.get(0).getMetadataJson()).contains("wide");
    }
}
