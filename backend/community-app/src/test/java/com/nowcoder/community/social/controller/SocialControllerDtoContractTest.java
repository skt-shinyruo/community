package com.nowcoder.community.social.controller;

import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.social.application.FollowApplicationService.FollowRelationResult;
import com.nowcoder.community.social.controller.dto.BlockRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;

class SocialControllerDtoContractTest {

    private final ObjectMapper objectMapper = JacksonJsonCodec.standardMapper();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void blockRequestRecordShouldPreserveJsonAndValidationContract() throws Exception {
        BlockRequest request = objectMapper.readValue("""
                {"userId":"00000000-0000-7000-8000-000000000008","unknown":"ignored"}
                """, BlockRequest.class);

        assertThat(request.userId()).isEqualTo(uuid(8));
        assertThat(objectMapper.valueToTree(request).fieldNames()).toIterable().containsExactly("userId");

        BlockRequest invalid = objectMapper.readValue("{}", BlockRequest.class);
        assertThat(validator.validate(invalid))
                .extracting(ConstraintViolation::getPropertyPath)
                .extracting(Object::toString)
                .containsExactly("userId");
    }

    @Test
    void followRelationResponseJsonShapeShouldStayStable() {
        FollowRelationResult result = new FollowRelationResult(
                uuid(8),
                Instant.parse("2026-07-06T10:00:00Z")
        );

        JsonNode json = objectMapper.valueToTree(result);

        assertThat(json.fieldNames()).toIterable().containsExactly("targetId", "followTime");
        assertThat(json.path("targetId").asText()).isEqualTo(uuid(8).toString());
        assertThat(json.path("followTime").asText()).isEqualTo("2026-07-06T10:00:00Z");
    }
}
