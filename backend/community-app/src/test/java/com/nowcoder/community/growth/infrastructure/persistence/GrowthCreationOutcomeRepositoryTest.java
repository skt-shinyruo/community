package com.nowcoder.community.growth.infrastructure.persistence;

import com.nowcoder.community.growth.domain.model.UserLevelRuleConfig;
import com.nowcoder.community.growth.domain.model.UserTaskProgress;
import com.nowcoder.community.growth.domain.repository.UserLevelRuleConfigRepository;
import com.nowcoder.community.growth.domain.repository.UserTaskEventLogRepository;
import com.nowcoder.community.growth.domain.repository.UserTaskProgressRepository;
import com.nowcoder.community.growth.infrastructure.persistence.dataobject.UserLevelRuleConfigDataObject;
import com.nowcoder.community.growth.infrastructure.persistence.dataobject.UserTaskProgressDataObject;
import com.nowcoder.community.growth.infrastructure.persistence.mapper.UserLevelRuleConfigMapper;
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
        UserTaskProgressDataObject existing = progressRow(1);
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

    @Test
    void levelConfigCreateShouldReloadSingletonBeforeReturningAlreadyExists() {
        UserLevelRuleConfigMapper mapper = mock(UserLevelRuleConfigMapper.class);
        UserLevelRuleConfig candidate = levelConfig(uuid(4), 120);
        UserLevelRuleConfigDataObject existing = UserLevelRuleConfigDataObject.from(levelConfig(uuid(5), 90));
        when(mapper.insert(any())).thenThrow(new DuplicateKeyException("uk_user_level_rule_config_key"));
        when(mapper.selectCurrent()).thenReturn(existing);

        UserLevelRuleConfigRepository.CreateResult result = new MyBatisUserLevelRuleConfigRepository(mapper)
                .create(candidate);

        assertThat(result.status()).isEqualTo(UserLevelRuleConfigRepository.CreateStatus.ALREADY_EXISTS);
        assertThat(result.config()).isSameAs(existing);
    }

    @Test
    void levelConfigCreateShouldNotMaskUnknownIntegrityFailure() {
        UserLevelRuleConfigMapper mapper = mock(UserLevelRuleConfigMapper.class);
        UserLevelRuleConfig candidate = levelConfig(uuid(4), 120);
        DataIntegrityViolationException failure = new DataIntegrityViolationException("unknown constraint");
        when(mapper.insert(any())).thenThrow(failure);
        when(mapper.selectCurrent()).thenReturn(UserLevelRuleConfigDataObject.from(levelConfig(uuid(5), 90)));

        assertThatThrownBy(() -> new MyBatisUserLevelRuleConfigRepository(mapper).create(candidate))
                .isSameAs(failure);
        verify(mapper, never()).selectCurrent();
    }

    private static UserTaskProgressDataObject progressRow(int targetValue) {
        UserTaskProgressDataObject progress = new UserTaskProgressDataObject();
        progress.setId(uuid(9));
        progress.setUserId(USER_ID);
        progress.setTaskCode(TASK_CODE);
        progress.setPeriodKey(PERIOD_KEY);
        progress.setCurrentValue(0);
        progress.setTargetValue(targetValue);
        progress.setStatus("IN_PROGRESS");
        return progress;
    }

    private static UserLevelRuleConfig levelConfig(UUID id, int windowDays) {
        UserLevelRuleConfig config = new UserLevelRuleConfig();
        config.setId(id);
        config.setWindowDays(windowDays);
        config.setLv2SignInDays(20);
        config.setLv3SignInDays(90);
        config.setEnabled(true);
        config.setUpdatedBy(USER_ID);
        return config;
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
