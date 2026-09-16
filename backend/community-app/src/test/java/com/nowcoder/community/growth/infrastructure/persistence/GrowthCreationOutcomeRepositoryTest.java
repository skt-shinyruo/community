package com.nowcoder.community.growth.infrastructure.persistence;

import com.nowcoder.community.growth.domain.model.UserTaskProgress;
import com.nowcoder.community.growth.domain.repository.UserTaskEventLogRepository;
import com.nowcoder.community.growth.domain.repository.UserTaskProgressRepository;
import com.nowcoder.community.growth.infrastructure.persistence.mapper.UserTaskEventLogMapper;
import com.nowcoder.community.growth.infrastructure.persistence.mapper.UserTaskProgressMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GrowthCreationOutcomeRepositoryTest {

    private static final UUID USER_ID = uuid(1);
    private static final String TASK_CODE = "DAILY_POST";
    private static final String PERIOD_KEY = "2026-07-15";
    private static final String EVENT_ID = "post-event-1";

    @Test
    void eventLogCreateShouldVerifyDuplicateTupleBeforeReturningAlreadyExists() {
        UserTaskEventLogMapper mapper = mock(UserTaskEventLogMapper.class);
        when(mapper.insert(any(), any(), any(), any(), any()))
                .thenThrow(new DuplicateKeyException("uk_user_task_event"));
        when(mapper.exists(USER_ID, TASK_CODE, PERIOD_KEY, EVENT_ID)).thenReturn(true);

        UserTaskEventLogRepository.CreateStatus status = new MyBatisUserTaskEventLogRepository(mapper)
                .create(uuid(2), USER_ID, TASK_CODE, PERIOD_KEY, EVENT_ID);

        assertThat(status).isEqualTo(UserTaskEventLogRepository.CreateStatus.ALREADY_EXISTS);
        verify(mapper).exists(USER_ID, TASK_CODE, PERIOD_KEY, EVENT_ID);
    }

    @Test
    void eventLogCreateShouldReturnConflictWhenIntegrityFailureDoesNotMatchTuple() {
        UserTaskEventLogMapper mapper = mock(UserTaskEventLogMapper.class);
        DataIntegrityViolationException failure = new DataIntegrityViolationException("unknown constraint");
        when(mapper.insert(any(), any(), any(), any(), any()))
                .thenThrow(failure);
        when(mapper.exists(USER_ID, TASK_CODE, PERIOD_KEY, EVENT_ID)).thenReturn(true);

        assertThatThrownBy(() -> new MyBatisUserTaskEventLogRepository(mapper)
                .create(uuid(2), USER_ID, TASK_CODE, PERIOD_KEY, EVENT_ID))
                .isSameAs(failure);
        verify(mapper, never()).exists(USER_ID, TASK_CODE, PERIOD_KEY, EVENT_ID);
    }

    @Test
    void progressCreateShouldReloadAndValidateConcurrentRow() {
        UserTaskProgressMapper mapper = mock(UserTaskProgressMapper.class);
        UserTaskProgress existing = progressRow(1);
        when(mapper.insert(any(), any(), any(), any(), any(Integer.class), any(), any()))
                .thenThrow(new DuplicateKeyException("uk_user_task_period"));
        when(mapper.selectByUserTaskAndPeriod(USER_ID, TASK_CODE, PERIOD_KEY)).thenReturn(existing);

        UserTaskProgressRepository.CreateResult result = new MyBatisUserTaskProgressRepository(mapper)
                .create(uuid(3), USER_ID, TASK_CODE, PERIOD_KEY, 1, "IN_PROGRESS", null);

        assertThat(result.status()).isEqualTo(UserTaskProgressRepository.CreateStatus.ALREADY_EXISTS);
        assertThat(result.progress()).isSameAs(existing);
        verify(mapper).selectByUserTaskAndPeriod(USER_ID, TASK_CODE, PERIOD_KEY);
    }

    @Test
    void progressCreateShouldRejectMismatchedConcurrentRow() {
        UserTaskProgressMapper mapper = mock(UserTaskProgressMapper.class);
        DataIntegrityViolationException failure = new DataIntegrityViolationException("unknown constraint");
        when(mapper.insert(any(), any(), any(), any(), any(Integer.class), any(), any()))
                .thenThrow(failure);
        when(mapper.selectByUserTaskAndPeriod(USER_ID, TASK_CODE, PERIOD_KEY)).thenReturn(progressRow(1));

        assertThatThrownBy(() -> new MyBatisUserTaskProgressRepository(mapper)
                .create(uuid(3), USER_ID, TASK_CODE, PERIOD_KEY, 1, "IN_PROGRESS", null))
                .isSameAs(failure);
        verify(mapper, never()).selectByUserTaskAndPeriod(USER_ID, TASK_CODE, PERIOD_KEY);
    }

    private static UserTaskProgress progressRow(int targetValue) {
        UserTaskProgress progress = new UserTaskProgress();
        progress.setId(uuid(9));
        progress.setUserId(USER_ID);
        progress.setTaskCode(TASK_CODE);
        progress.setPeriodKey(PERIOD_KEY);
        progress.setCurrentValue(0);
        progress.setTargetValue(targetValue);
        progress.setStatus("IN_PROGRESS");
        return progress;
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
