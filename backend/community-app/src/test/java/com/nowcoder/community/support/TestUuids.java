package com.nowcoder.community.support;

import java.util.UUID;

public final class TestUuids {

    private TestUuids() {
    }

    public static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
