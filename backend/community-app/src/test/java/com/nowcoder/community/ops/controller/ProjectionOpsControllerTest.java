package com.nowcoder.community.ops.controller;

import com.nowcoder.community.app.security.CommunitySecurityConfig;
import com.nowcoder.community.common.web.GlobalExceptionHandler;
import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.common.web.SecurityExceptionHandler;
import com.nowcoder.community.ops.application.ProjectionLagQuery;
import com.nowcoder.community.ops.security.OpsSecurityRules;
import com.nowcoder.community.support.WebMvcSliceJsonCodecTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProjectionOpsController.class)
@Import({
        ProjectionOpsController.class,
        OpsSecurityRules.class,
        CommunitySecurityConfig.class,
        WebMvcSliceJsonCodecTestConfig.class,
        SecurityExceptionHandler.class,
        GlobalExceptionHandler.class
})
class ProjectionOpsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectionOpsController controller;

    @MockitoBean
    private ProjectionLagQuery projectionLagQuery;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    @Test
    void nonAdminRequestsShouldBeRejected() throws Exception {
        mockMvc.perform(get("/api/ops/projections/lag")
                        .with(jwt().jwt(jwt -> jwt.subject("user-1")).authorities(() -> "ROLE_USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminShouldQueryProjectionLagUsingApplicationReadModel() throws Exception {
        when(projectionLagQuery.listProjectionLag())
                .thenReturn(List.of(new ProjectionLagQuery.ProjectionLag(
                        "eventbus.content",
                        "PENDING",
                        2L,
                        Duration.ofSeconds(42)
                )));

        Result<List<ProjectionLagQuery.ProjectionLag>> result = controller.lag();

        assertThat(result.getData()).singleElement().isInstanceOf(ProjectionLagQuery.ProjectionLag.class);

        mockMvc.perform(get("/api/ops/projections/lag")
                        .with(jwt().jwt(jwt -> jwt.subject("admin-1")).authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].projection").value("eventbus.content"))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data[0].count").value(2))
                .andExpect(jsonPath("$.data[0].oldestAge").value("PT42S"));

        verify(projectionLagQuery, times(2)).listProjectionLag();
    }
}
