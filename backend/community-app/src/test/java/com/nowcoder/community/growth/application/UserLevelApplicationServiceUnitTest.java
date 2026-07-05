package com.nowcoder.community.growth.application;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.growth.application.command.UpdateUserLevelConfigCommand;
import com.nowcoder.community.growth.application.result.UserLevelConfigResult;
import com.nowcoder.community.growth.domain.repository.UserLevelRuleConfigRepository;
import com.nowcoder.community.growth.domain.repository.UserTaskProgressRepository;
import com.nowcoder.community.growth.domain.service.UserLevelDomainService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserLevelApplicationServiceUnitTest {

    @Test
    void updateConfigShouldRejectNullCommand() {
        UserLevelApplicationService service = new UserLevelApplicationService(
                mock(UserTaskProgressRepository.class),
                mock(UserLevelRuleConfigRepository.class),
                mock(GrowthBusinessTimeService.class),
                new UserLevelDomainService(),
                new UuidV7Generator()
        );

        assertThatThrownBy(() -> service.updateConfig((UpdateUserLevelConfigCommand) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }

    @Test
    void updateConfigWithActorShouldRejectNullCommand() {
        UserLevelApplicationService service = new UserLevelApplicationService(
                mock(UserTaskProgressRepository.class),
                mock(UserLevelRuleConfigRepository.class),
                mock(GrowthBusinessTimeService.class),
                new UserLevelDomainService(),
                new UuidV7Generator()
        );

        assertThatThrownBy(() -> service.updateConfig(uuid(99), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }

    @Test
    void updateConfigShouldRetryUpdateWhenInsertHitsDuplicateKeyOnFirstWriteRace() {
        UserTaskProgressRepository userTaskProgressRepository = mock(UserTaskProgressRepository.class);
        UserLevelRuleConfigRepository userLevelRuleConfigRepository = mock(UserLevelRuleConfigRepository.class);
        GrowthBusinessTimeService growthBusinessTimeService = mock(GrowthBusinessTimeService.class);
        UserLevelApplicationService service = new UserLevelApplicationService(
                userTaskProgressRepository,
                userLevelRuleConfigRepository,
                growthBusinessTimeService,
                new UserLevelDomainService(),
                new UuidV7Generator()
        );

        UpdateUserLevelConfigCommand request = new UpdateUserLevelConfigCommand();
        request.setWindowDays(120);
        request.setLv2SignInDays(20);
        request.setLv3SignInDays(90);
        request.setEnabled(true);

        when(userLevelRuleConfigRepository.updateCurrent(any())).thenReturn(0, 1);
        doThrow(new DuplicateKeyException("duplicate key")).when(userLevelRuleConfigRepository).insert(any());

        UserLevelConfigResult response = service.updateConfig(uuid(99), request);

        assertThat(response.getWindowDays()).isEqualTo(120);
        assertThat(response.getLv2SignInDays()).isEqualTo(20);
        assertThat(response.getLv3SignInDays()).isEqualTo(90);
        assertThat(response.isEnabled()).isTrue();
        verify(userLevelRuleConfigRepository).insert(any());
        verify(userLevelRuleConfigRepository, times(2)).updateCurrent(any());
    }
}
