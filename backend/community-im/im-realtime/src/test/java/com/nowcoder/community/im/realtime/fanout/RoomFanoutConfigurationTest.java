package com.nowcoder.community.im.realtime.fanout;

import com.nowcoder.community.im.realtime.presence.RedisRoomPresenceDirectory;
import com.nowcoder.community.im.realtime.presence.RoomPresenceConfiguration;
import com.nowcoder.community.im.realtime.presence.RoomPresenceDirectory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RoomFanoutConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(RoomFanoutConfiguration.class);

    @Test
    void routedOwnerTargetAndKafkaDispatcherAreUnconditional() {
        assertThat(RoomPersistedOwnerConsumer.class.getAnnotation(ConditionalOnExpression.class)).isNull();
        assertThat(RoomFanoutTargetConsumer.class.getAnnotation(ConditionalOnExpression.class)).isNull();
        assertThat(KafkaRoomFanoutDispatcher.class.getAnnotation(ConditionalOnExpression.class)).isNull();
        assertThat(KafkaRoomFanoutDispatcher.class.getAnnotation(Primary.class)).isNull();
    }

    @Test
    void missingWorkerInboxSlotFailsStartup() {
        contextRunner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalStateException.class)
                    .hasRootCauseMessage(
                            "im.room-fanout.worker-inbox-slot is required and must be between 0 and 63"
                    );
        });
    }

    @Test
    void explicitWorkerInboxSlotWithinFixedPartitionRangeStarts() {
        contextRunner
                .withPropertyValues(
                        "im.room-fanout.worker-inbox-slot=0",
                        "im.room-fanout.routed-command-partitions=64"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RoomFanoutProperties.class);
                    assertThat(context.getBean(RoomFanoutProperties.class).normalizedWorkerInboxSlot()).isZero();
                });
    }

    @Test
    void workerInboxSlotAtFixedPartitionCountFailsStartup() {
        contextRunner
                .withPropertyValues(
                        "im.room-fanout.worker-inbox-slot=64",
                        "im.room-fanout.routed-command-partitions=64"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasRootCauseMessage(
                                    "im.room-fanout.worker-inbox-slot is required and must be between 0 and 63"
                            );
                });
    }

    @Test
    void routedCommandPartitionCountMustRemainFixedAt64() {
        contextRunner
                .withPropertyValues(
                        "im.room-fanout.worker-inbox-slot=0",
                        "im.room-fanout.routed-command-partitions=63"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasRootCauseMessage("im.room-fanout.routed-command-partitions must be 64");
                });
    }

    @Test
    void redisPresenceDirectoryIsTheOnlyPresenceImplementation() {
        new ApplicationContextRunner()
                .withUserConfiguration(RoomPresenceConfiguration.class)
                .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RoomPresenceDirectory.class);
                    assertThat(context.getBean(RoomPresenceDirectory.class))
                            .isInstanceOf(RedisRoomPresenceDirectory.class);
                    assertThat(context).doesNotHaveBean("noopRoomPresenceDirectory");
                });
    }

    @Test
    void missingRedisTemplateFailsPresenceWiring() {
        new ApplicationContextRunner()
                .withUserConfiguration(RoomPresenceConfiguration.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(org.springframework.beans.factory.NoSuchBeanDefinitionException.class);
                });
    }
}
