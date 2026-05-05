package com.nowcoder.community.im.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImCommonContractRetirementTest {

    @Test
    void retiredImCommonContractsShouldStayAbsent() {
        assertClassRetired(cn("com.nowcoder.community.im.common.session.", "Open", "ImSessionRequest"));
        assertClassRetired(cn("com.nowcoder.community.im.common.event.", "RoomMemberChanged", "EventV1"));
    }

    private void assertClassRetired(String className) {
        assertThatThrownBy(() -> Class.forName(className))
                .isInstanceOf(ClassNotFoundException.class);
    }

    private String cn(String... parts) {
        return String.join("", parts);
    }
}
