package com.nowcoder.community.im.common;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

public final class ImSchemaVersionDeserializer extends JsonDeserializer<Integer> {

    @Override
    public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
            return context.reportInputMismatch(
                    Integer.class,
                    "IM schemaVersion must be a JSON integer"
            );
        }
        return ImContractVersions.requireSupportedSchemaVersion(parser.getIntValue());
    }

    @Override
    public Integer getNullValue(DeserializationContext context) {
        throw new ImUnsupportedSchemaVersionException(
                0,
                ImContractVersions.CURRENT_SCHEMA_VERSION
        );
    }
}
