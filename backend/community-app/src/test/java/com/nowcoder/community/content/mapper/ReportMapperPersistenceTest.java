package com.nowcoder.community.content.mapper;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.content.entity.Report;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Date;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class ReportMapperPersistenceTest {

    private static final UUID REPORT_ID = UUID.fromString("00000000-0000-7000-8000-000000000201");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ReportMapper reportMapper;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from moderation_action");
        jdbcTemplate.update("delete from report");
    }

    @Test
    void insertReportShouldPersistApplicationAssignedUuidPrimaryKey() {
        UUID reporterId = uuid(7);
        UUID targetId = uuid(88);
        Report report = new Report();
        report.setId(REPORT_ID);
        report.setReporterId(reporterId);
        report.setTargetType(1);
        report.setTargetId(targetId);
        report.setReason("spam");
        report.setDetail("details");
        report.setStatus(0);
        report.setCreateTime(new Date());

        int inserted = reportMapper.insertReport(report);

        assertThat(inserted).isEqualTo(1);
        assertThat(report.getId()).isEqualTo(REPORT_ID);

        byte[] storedId = jdbcTemplate.queryForObject(
                "select id from report where reporter_id = ? and target_type = ? and target_id = ?",
                (rs, rowNum) -> rs.getBytes(1),
                BinaryUuidCodec.toBytes(reporterId),
                1,
                BinaryUuidCodec.toBytes(targetId)
        );
        assertThat(storedId).hasSize(16);
        assertThat(BinaryUuidCodec.fromBytes(storedId)).isEqualTo(REPORT_ID);

        UUID dedupedId = reportMapper.selectReportIdByDedupeKey(reporterId, 1, targetId);
        assertThat(dedupedId).isEqualTo(REPORT_ID);

        Report persisted = reportMapper.selectReportById(REPORT_ID);
        assertThat(persisted).isNotNull();
        assertThat(persisted.getId()).isEqualTo(REPORT_ID);
    }
}
