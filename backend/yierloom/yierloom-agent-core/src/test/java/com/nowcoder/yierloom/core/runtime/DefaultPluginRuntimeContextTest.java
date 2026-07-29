package com.nowcoder.yierloom.core.runtime;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import com.nowcoder.yierloom.api.EventSink;
import com.nowcoder.yierloom.api.ManagedScheduler;
import com.nowcoder.yierloom.api.ObservationChannel;
import com.nowcoder.yierloom.api.PluginConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class DefaultPluginRuntimeContextTest {

    @Test
    void exposesTheScopedDependenciesWithoutSubstitution() {
        PluginConfig config = PluginConfig.empty();
        ManagedScheduler scheduler = (name, initialDelay, delay, task) -> null;
        ObservationChannel observations = handler -> { };
        EventSink events = event -> true;
        System.Logger logger = System.getLogger("yierloom-test");
        Clock clock = Clock.fixed(Instant.parse("2026-07-29T10:15:30Z"), ZoneOffset.UTC);

        DefaultPluginRuntimeContext context = new DefaultPluginRuntimeContext(
                config, scheduler, observations, events, logger, clock);

        assertThat(context.config()).isSameAs(config);
        assertThat(context.scheduler()).isSameAs(scheduler);
        assertThat(context.observations()).isSameAs(observations);
        assertThat(context.events()).isSameAs(events);
        assertThat(context.logger()).isSameAs(logger);
        assertThat(context.clock()).isSameAs(clock);
    }

    @Test
    void rejectsNullDependenciesAtConstruction() {
        PluginConfig config = PluginConfig.empty();
        ManagedScheduler scheduler = (name, initialDelay, delay, task) -> null;
        ObservationChannel observations = handler -> { };
        EventSink events = event -> true;
        System.Logger logger = System.getLogger("yierloom-test");
        Clock clock = Clock.systemUTC();

        assertThatNullPointerException().isThrownBy(() -> new DefaultPluginRuntimeContext(
                null, scheduler, observations, events, logger, clock));
        assertThatNullPointerException().isThrownBy(() -> new DefaultPluginRuntimeContext(
                config, null, observations, events, logger, clock));
        assertThatNullPointerException().isThrownBy(() -> new DefaultPluginRuntimeContext(
                config, scheduler, null, events, logger, clock));
        assertThatNullPointerException().isThrownBy(() -> new DefaultPluginRuntimeContext(
                config, scheduler, observations, null, logger, clock));
        assertThatNullPointerException().isThrownBy(() -> new DefaultPluginRuntimeContext(
                config, scheduler, observations, events, null, clock));
        assertThatNullPointerException().isThrownBy(() -> new DefaultPluginRuntimeContext(
                config, scheduler, observations, events, logger, null));
    }
}
