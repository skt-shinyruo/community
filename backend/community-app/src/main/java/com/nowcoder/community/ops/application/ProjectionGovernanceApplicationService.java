package com.nowcoder.community.ops.application;

import com.nowcoder.community.ops.application.result.ProjectionLagResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class ProjectionGovernanceApplicationService {

    private final ProjectionLagPort projectionLagPort;

    public ProjectionGovernanceApplicationService(ProjectionLagPort projectionLagPort) {
        this.projectionLagPort = Objects.requireNonNull(projectionLagPort, "projectionLagPort must not be null");
    }

    public List<ProjectionLagResult> listProjectionLag() {
        return projectionLagPort.listProjectionLag();
    }
}
