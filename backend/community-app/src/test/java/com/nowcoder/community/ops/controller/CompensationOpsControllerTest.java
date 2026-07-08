package com.nowcoder.community.ops.controller;

import com.nowcoder.community.app.security.CommunitySecurityConfig;
import com.nowcoder.community.common.web.GlobalExceptionHandler;
import com.nowcoder.community.common.web.SecurityExceptionHandler;
import com.nowcoder.community.ops.application.CompensationGovernanceApplicationService;
import com.nowcoder.community.ops.application.command.TriggerCompensationCommand;
import com.nowcoder.community.ops.application.result.CompensationTriggerResult;
import com.nowcoder.community.ops.security.OpsSecurityRules;
import com.nowcoder.community.support.WebMvcSliceJsonCodecTestConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CompensationOpsController.class)
@Import({
        CompensationOpsController.class,
        OpsSecurityRules.class,
        CommunitySecurityConfig.class,
        WebMvcSliceJsonCodecTestConfig.class,
        SecurityExceptionHandler.class,
        GlobalExceptionHandler.class
})
class CompensationOpsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CompensationGovernanceApplicationService compensationGovernanceApplicationService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    @Test
    void nonAdminRequestsShouldBeRejected() throws Exception {
        mockMvc.perform(post("/api/ops/compensations/outboxRecoverExpiredLeases/trigger")
                        .with(jwt().jwt(jwt -> jwt.subject(uuid(2).toString())).authorities(() -> "ROLE_USER"))
                        .contentType("application/json")
                        .content("{\"limit\":10,\"reason\":\"recover\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminShouldTriggerCompensationJobWithActorFromJwt() throws Exception {
        UUID adminUserId = uuid(99);
        when(compensationGovernanceApplicationService.trigger(any()))
                .thenReturn(new CompensationTriggerResult(
                        "outboxRecoverExpiredLeases",
                        true,
                        10,
                        7,
                        3,
                        "ACCEPTED",
                        "expired outbox leases recovered"
                ));

        mockMvc.perform(post("/api/ops/compensations/outboxRecoverExpiredLeases/trigger")
                        .with(jwt().jwt(jwt -> jwt.subject(adminUserId.toString())).authorities(() -> "ROLE_ADMIN"))
                        .contentType("application/json")
                        .content("{\"limit\":10,\"reason\":\"recover expired workers\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobName").value("outboxRecoverExpiredLeases"))
                .andExpect(jsonPath("$.data.accepted").value(true))
                .andExpect(jsonPath("$.data.processedCount").value(10))
                .andExpect(jsonPath("$.data.repairedCount").value(7))
                .andExpect(jsonPath("$.data.skippedCount").value(3))
                .andExpect(jsonPath("$.data.result").value("ACCEPTED"));

        ArgumentCaptor<TriggerCompensationCommand> commandCaptor =
                ArgumentCaptor.forClass(TriggerCompensationCommand.class);
        verify(compensationGovernanceApplicationService).trigger(commandCaptor.capture());
        TriggerCompensationCommand command = commandCaptor.getValue();
        assertAll(
                () -> assertEquals(adminUserId, command.actorUserId()),
                () -> assertEquals("outboxRecoverExpiredLeases", command.jobName()),
                () -> assertEquals(10, command.limit()),
                () -> assertEquals("recover expired workers", command.reason())
        );
    }

    @Test
    void triggerShouldRejectInvalidRequestBody() throws Exception {
        mockMvc.perform(post("/api/ops/compensations/outboxRecoverExpiredLeases/trigger")
                        .with(jwt().jwt(jwt -> jwt.subject(uuid(99).toString())).authorities(() -> "ROLE_ADMIN"))
                        .contentType("application/json")
                        .content("{\"limit\":0,\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
