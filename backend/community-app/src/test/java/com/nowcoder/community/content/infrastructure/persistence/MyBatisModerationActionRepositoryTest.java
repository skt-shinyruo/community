package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.content.domain.model.ModerationAction;
import com.nowcoder.community.content.domain.model.ModerationActionRecord;
import com.nowcoder.community.content.infrastructure.persistence.mapper.ModerationActionMapper;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MyBatisModerationActionRepositoryTest {

    private static final UUID REPORT_ID = uuid(50);

    private final ModerationActionMapper mapper = mock(ModerationActionMapper.class);
    private final MyBatisModerationActionRepository repository =
            new MyBatisModerationActionRepository(mapper, new UuidV7Generator());

    @Test
    void findByReportIdShouldReturnEmptyWhenNoActionExists() {
        when(mapper.selectActionsByReportId(REPORT_ID)).thenReturn(List.of());

        assertThat(repository.findByReportId(REPORT_ID)).isEmpty();
    }

    @Test
    void findByReportIdShouldReturnTheOnlyAction() {
        ModerationAction action = action(uuid(51), "ban");
        when(mapper.selectActionsByReportId(REPORT_ID)).thenReturn(List.of(action));

        assertThat(repository.findByReportId(REPORT_ID))
                .contains(new ModerationActionRecord(
                        action.getId(),
                        REPORT_ID,
                        action.getActorId(),
                        "ban",
                        "abuse",
                        3600,
                        action.getCreateTime()
                ));
    }

    @Test
    void findByReportIdShouldFailClosedWhenHistoricalDuplicatesExist() {
        when(mapper.selectActionsByReportId(REPORT_ID)).thenReturn(List.of(
                action(uuid(51), "ban"),
                action(uuid(52), "mute")
        ));

        assertThatThrownBy(() -> repository.findByReportId(REPORT_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(REPORT_ID.toString())
                .hasMessageContaining("2");
    }

    private ModerationAction action(UUID id, String action) {
        ModerationAction row = new ModerationAction();
        row.setId(id);
        row.setReportId(REPORT_ID);
        row.setActorId(uuid(42));
        row.setAction(action);
        row.setReason("abuse");
        row.setDurationSeconds(3600);
        row.setCreateTime(new Date(1_750_000_000_000L));
        return row;
    }
}
