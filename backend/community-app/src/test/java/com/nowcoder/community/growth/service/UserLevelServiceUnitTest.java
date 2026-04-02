package com.nowcoder.community.growth.service;

import com.nowcoder.community.growth.dto.UpdateUserLevelConfigRequest;
import com.nowcoder.community.growth.dto.UserLevelConfigResponse;
import com.nowcoder.community.growth.mapper.GrowthCheckInMapper;
import com.nowcoder.community.growth.mapper.UserLevelRuleConfigMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserLevelServiceUnitTest {

    @Test
    void updateConfigShouldRetryUpdateWhenInsertHitsDuplicateKeyOnFirstWriteRace() {
        GrowthCheckInMapper growthCheckInMapper = mock(GrowthCheckInMapper.class);
        UserLevelRuleConfigMapper userLevelRuleConfigMapper = mock(UserLevelRuleConfigMapper.class);
        GrowthBusinessTimeService growthBusinessTimeService = mock(GrowthBusinessTimeService.class);
        UserLevelService service = new UserLevelService(
                growthCheckInMapper,
                userLevelRuleConfigMapper,
                growthBusinessTimeService
        );

        UpdateUserLevelConfigRequest request = new UpdateUserLevelConfigRequest();
        request.setWindowDays(120);
        request.setLv2SignInDays(20);
        request.setLv3SignInDays(90);
        request.setEnabled(true);

        when(userLevelRuleConfigMapper.updateCurrent(any())).thenReturn(0, 1);
        doThrow(new DuplicateKeyException("duplicate key")).when(userLevelRuleConfigMapper).insert(any());

        UserLevelConfigResponse response = service.updateConfig(99, request);

        assertThat(response.getWindowDays()).isEqualTo(120);
        assertThat(response.getLv2SignInDays()).isEqualTo(20);
        assertThat(response.getLv3SignInDays()).isEqualTo(90);
        assertThat(response.isEnabled()).isTrue();
        verify(userLevelRuleConfigMapper).insert(any());
        verify(userLevelRuleConfigMapper, times(2)).updateCurrent(any());
    }
}
