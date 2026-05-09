package com.nowcoder.community.content.infrastructure.persistence.mapper;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.content.infrastructure.persistence.dataobject.PostMediaAssetDataObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = CommunityAppApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class PostMediaAssetMapperPersistenceTest {

    private static final UUID ASSET_ID = UUID.fromString("00000000-0000-7000-8000-000000000701");
    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-7000-8000-000000000702");
    private static final UUID POST_ID = UUID.fromString("00000000-0000-7000-8000-000000000703");
    private static final UUID OBJECT_ID = UUID.fromString("00000000-0000-7000-8000-000000000704");
    private static final UUID VERSION_ID = UUID.fromString("00000000-0000-7000-8000-000000000705");
    private static final UUID REFERENCE_ID = UUID.fromString("00000000-0000-7000-8000-000000000706");

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PostMediaAssetMapper mapper;

    @MockBean
    ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from post_media_asset");
    }

    @Test
    void insertMarkUploadedAndBindShouldPersistAssetLifecycle() {
        PostMediaAssetDataObject row = new PostMediaAssetDataObject();
        row.setId(ASSET_ID);
        row.setOwnerUserId(OWNER_ID);
        row.setOssObjectId(OBJECT_ID);
        row.setOssVersionId(VERSION_ID);
        row.setUploadSessionId(UUID.fromString("00000000-0000-7000-8000-000000000707"));
        row.setFileName("demo.mp4");
        row.setContentType("video/mp4");
        row.setContentLength(1234L);
        row.setMediaKind("VIDEO");
        row.setLifecycle("DRAFT");
        row.setVideoState("NONE");
        row.setPublicUrl("");
        row.setCreateTime(Timestamp.from(Instant.parse("2026-05-09T00:00:00Z")));

        assertThat(mapper.insert(row)).isEqualTo(1);
        assertThat(mapper.markUploaded(
                ASSET_ID,
                VERSION_ID,
                "http://localhost/files/demo.mp4",
                Timestamp.from(Instant.parse("2026-05-09T00:01:00Z"))
        )).isEqualTo(1);
        assertThat(mapper.bindToPost(
                ASSET_ID,
                POST_ID,
                REFERENCE_ID,
                "PENDING_TRANSCODE",
                Timestamp.from(Instant.parse("2026-05-09T00:02:00Z"))
        )).isEqualTo(1);

        PostMediaAssetDataObject saved = mapper.selectById(ASSET_ID);
        assertThat(saved.getLifecycle()).isEqualTo("BOUND");
        assertThat(saved.getPostId()).isEqualTo(POST_ID);
        assertThat(saved.getOssReferenceId()).isEqualTo(REFERENCE_ID);
        assertThat(saved.getVideoState()).isEqualTo("PENDING_TRANSCODE");
        assertThat(saved.getPublicUrl()).isEqualTo("http://localhost/files/demo.mp4");
    }
}
