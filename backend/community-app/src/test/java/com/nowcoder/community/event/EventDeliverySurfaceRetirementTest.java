package com.nowcoder.community.event;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventDeliverySurfaceRetirementTest {

    @Test
    void retiredEventAdaptersShouldNotRemainOnClasspath() {
        assertClassRetired("com.nowcoder.community.user.event.PointsProjectionListener");
        assertClassRetired("com.nowcoder.community.user.event.PointsOutboxEnqueuer");
        assertClassRetired("com.nowcoder.community.user.event.PointsOutboxHandler");
        assertClassRetired("com.nowcoder.community.growth.event.TaskProgressProjectionListener");
        assertClassRetired("com.nowcoder.community.growth.event.TaskProgressOutboxEnqueuer");
        assertClassRetired("com.nowcoder.community.growth.event.TaskProgressOutboxHandler");
        assertClassRetired("com.nowcoder.community.notice.event.NoticeOutboxEnqueuer");
        assertClassRetired("com.nowcoder.community.notice.event.NoticeOutboxHandler");
        assertClassRetired("com.nowcoder.community.search.event.PostProjectionListener");
        assertClassRetired("com.nowcoder.community.growth.event.GrowthEventPublisher");
        assertClassRetired("com.nowcoder.community.growth.event.LocalGrowthEventPublisher");
        assertClassRetired("com.nowcoder.community.growth.event.GrowthLocalEvent");
        assertClassRetired("com.nowcoder.community.growth.event.payload.CheckInPayload");
    }

    private void assertClassRetired(String className) {
        assertThatThrownBy(() -> Class.forName(className))
                .isInstanceOf(ClassNotFoundException.class);
    }
}
