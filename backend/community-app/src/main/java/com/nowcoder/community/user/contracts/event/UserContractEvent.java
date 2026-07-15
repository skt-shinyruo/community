package com.nowcoder.community.user.contracts.event;

import com.fasterxml.jackson.databind.JsonNode;

public record UserContractEvent(String eventId, String type, JsonNode payload) {
}
