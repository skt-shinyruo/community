package com.nowcoder.community.im.common.session;

import java.util.Map;

public record OpenImSessionRequest(Map<String, String> metadata) {
}
