package com.nowcoder.community.user.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.user.api.model.UserSummaryView;
import com.nowcoder.community.user.application.AdminUserApplicationService;
import com.nowcoder.community.user.controller.dto.AvatarUploadSessionRequest;
import com.nowcoder.community.user.controller.dto.AvatarUploadSessionResponse;
import com.nowcoder.community.user.controller.dto.BatchUserSummaryRequest;
import com.nowcoder.community.user.controller.dto.UpdateAvatarRequest;
import com.nowcoder.community.user.controller.dto.UpdateUserRoleRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserControllerDtoContractTest {

    private final ObjectMapper objectMapper = JsonMappers.standard();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void requestJsonDefaultsValidationAndUnknownFieldBehaviorStayStable() throws Exception {
        AvatarUploadSessionRequest upload = objectMapper.readValue("""
                {
                  "fileName": "avatar.png",
                  "contentType": "image/png",
                  "contentLength": 16,
                  "unknown": "ignored"
                }
                """, AvatarUploadSessionRequest.class);
        assertThat(upload.fileName()).isEqualTo("avatar.png");
        assertThat(upload.contentType()).isEqualTo("image/png");
        assertThat(upload.contentLength()).isEqualTo(16L);
        assertThat(upload.checksumSha256()).isNull();
        assertThat(validator.validate(upload)).isEmpty();

        AvatarUploadSessionRequest invalidUpload = objectMapper.readValue("{}", AvatarUploadSessionRequest.class);
        assertThat(invalidPropertyNames(invalidUpload))
                .containsExactlyInAnyOrder("fileName", "contentType", "contentLength");

        BatchUserSummaryRequest batch = objectMapper.readValue("""
                {"userIds":["00000000-0000-7000-8000-000000000007"]}
                """, BatchUserSummaryRequest.class);
        assertThat(batch.userIds()).containsExactly(uuid(7));

        UpdateAvatarRequest avatar = objectMapper.readValue("{}", UpdateAvatarRequest.class);
        assertThat(avatar.objectId()).isNull();
        assertThat(invalidPropertyNames(avatar)).containsExactly("objectId");

        UpdateUserRoleRequest role = objectMapper.readValue("""
                {"targetUserId":"00000000-0000-7000-8000-000000000008","type":2,"reason":"moderate"}
                """, UpdateUserRoleRequest.class);
        assertThat(role.targetUserId()).isEqualTo(uuid(8));
        assertThat(role.type()).isEqualTo(2);
        assertThat(role.reason()).isEqualTo("moderate");
        assertThat(role.confirm()).isFalse();
        assertThat(validator.validate(role)).isEmpty();
    }

    @Test
    void responseJsonShapeStayStable() throws Exception {
        AvatarUploadSessionResponse response = new AvatarUploadSessionResponse(
                "upload-1",
                uuid(10).toString(),
                uuid(11).toString(),
                new AvatarUploadSessionResponse.UploadInstruction(
                        "/api/oss/objects/10/complete",
                        "POST",
                        "file",
                        Map.of("sessionId", "upload-1"),
                        Map.of("X-Upload", "one")
                ),
                new AvatarUploadSessionResponse.Constraints(
                        2_097_152L,
                        List.of("image/png", "image/jpeg")
                ),
                "2026-05-08T12:00:00Z"
        );

        JsonNode avatarJson = objectMapper.valueToTree(response);
        assertThat(avatarJson.fieldNames()).toIterable()
                .containsExactly("uploadId", "objectId", "versionId", "upload", "constraints", "expiresAt");
        assertThat(avatarJson.at("/upload/url").asText()).isEqualTo("/api/oss/objects/10/complete");
        assertThat(avatarJson.at("/upload/fields/sessionId").asText()).isEqualTo("upload-1");
        assertThat(avatarJson.at("/constraints/maxBytes").asLong()).isEqualTo(2_097_152L);
        assertThat(avatarJson.at("/constraints/mimeTypes/1").asText()).isEqualTo("image/jpeg");

        AdminUserApplicationService.AdminUserResult admin = new AdminUserApplicationService.AdminUserResult(
                uuid(12),
                "alice",
                "alice@example.com",
                2,
                1,
                "h12",
                Date.from(Instant.parse("2026-05-08T12:00:00Z"))
        );
        assertThat(objectMapper.valueToTree(admin).fieldNames()).toIterable()
                .containsExactly("id", "username", "email", "type", "status", "headerUrl", "createTime");

        UserSummaryView summary = new UserSummaryView(uuid(13), "bob", "h13", 1);
        assertThat(objectMapper.valueToTree(summary).fieldNames()).toIterable()
                .containsExactly("id", "username", "headerUrl", "type");
    }

    private Set<String> invalidPropertyNames(Object value) {
        return validator.validate(value).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(java.util.stream.Collectors.toSet());
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
