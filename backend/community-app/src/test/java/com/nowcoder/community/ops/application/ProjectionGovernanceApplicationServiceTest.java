package com.nowcoder.community.ops.application;

import com.nowcoder.community.ops.application.result.ProjectionLagResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectionGovernanceApplicationServiceTest {

    @Test
    void listProjectionLagShouldReturnPortRows() {
        ProjectionLagPort port = mock(ProjectionLagPort.class);
        when(port.listProjectionLag()).thenReturn(List.of(new ProjectionLagResult(
                "projection.search.post",
                "PENDING",
                3L,
                Duration.ofSeconds(42)
        )));
        ProjectionGovernanceApplicationService service = new ProjectionGovernanceApplicationService(port);

        List<ProjectionLagResult> result = service.listProjectionLag();

        assertThat(result).containsExactly(new ProjectionLagResult(
                "projection.search.post",
                "PENDING",
                3L,
                Duration.ofSeconds(42)
        ));
    }
}
