package com.nowcoder.community.user.contracts.event;

import com.fasterxml.jackson.databind.JsonNode;

public sealed interface UserTypedEvent permits
        UserTypedEvent.UserPolicyChanged,
        UserTypedEvent.Unknown {

    String eventId();

    record UserPolicyChanged(
            String eventId,
            UserPolicyChangedPayload payload
    ) implements UserTypedEvent {
    }

    record Unknown(
            String eventId,
            String type,
            JsonNode payload
    ) implements UserTypedEvent {
    }
}
