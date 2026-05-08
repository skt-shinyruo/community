package com.nowcoder.community.oss.controller;

import com.nowcoder.community.oss.application.ObjectReferenceApplicationService;
import com.nowcoder.community.oss.application.result.ObjectReferenceResult;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InternalOssObjectControllerTest {

    @Test
    void bindAndReleaseReferenceShouldReturnReferenceState() throws Exception {
        UUID objectId = uuid(1);
        UUID referenceId = uuid(3);
        ObjectReferenceApplicationService referenceService = mock(ObjectReferenceApplicationService.class);
        when(referenceService.bindReference(any())).thenReturn(new ObjectReferenceResult(
                referenceId,
                objectId,
                uuid(2),
                "community-app",
                "user",
                "avatar",
                "7",
                "PRIMARY",
                "ACTIVE",
                null,
                Instant.parse("2026-05-07T00:00:00Z"),
                null
        ));
        when(referenceService.releaseReference(any())).thenReturn(new ObjectReferenceResult(
                referenceId,
                objectId,
                uuid(2),
                "community-app",
                "user",
                "avatar",
                "7",
                "PRIMARY",
                "RELEASED",
                null,
                Instant.parse("2026-05-07T00:00:00Z"),
                Instant.parse("2026-05-07T00:05:00Z")
        ));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new InternalOssObjectController(referenceService)).build();

        mvc.perform(post("/internal/oss/objects/{objectId}/references", objectId)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "versionId": "%s",
                                  "subjectService": "community-app",
                                  "subjectDomain": "user",
                                  "subjectType": "avatar",
                                  "subjectId": "7",
                                  "referenceRole": "PRIMARY",
                                  "retainUntil": "2026-05-07T01:00:00Z",
                                  "actorId": "7"
                                }
                                """.formatted(uuid(2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.referenceRole").value("PRIMARY"));

        mvc.perform(delete("/internal/oss/objects/{objectId}/references/{referenceId}", objectId, referenceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"));
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
