package com.nowcoder.community.im.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImCommonContractRetirementTest {

    @Test
    void retiredImCommonContractsShouldStayAbsent() {
        assertClassRetired("com.nowcoder.community.im.common.session.OpenImSessionRequest");
        assertClassRetired("com.nowcoder.community.im.common.event.RoomMemberChangedEventV1");
    }

    private void assertClassRetired(String className) {
        assertThatThrownBy(() -> Class.forName(className))
                .isInstanceOf(ClassNotFoundException.class);
    }
}
