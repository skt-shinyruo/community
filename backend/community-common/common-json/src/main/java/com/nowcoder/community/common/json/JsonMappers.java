package com.nowcoder.community.common.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.nowcoder.community.common.event.EventEnvelope;

public final class JsonMappers {

    private JsonMappers() {
    }

    public static ObjectMapper standard() {
        return standardBuilder().build();
    }

    public static JsonMapper.Builder standardBuilder() {
        return JsonMapper.builder()
                .findAndAddModules()
                .addMixIn(EventEnvelope.class, EventEnvelopeMixin.class)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private abstract static class EventEnvelopeMixin {
    }
}
