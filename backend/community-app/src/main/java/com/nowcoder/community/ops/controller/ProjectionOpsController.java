package com.nowcoder.community.ops.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.ops.application.ProjectionGovernanceApplicationService;
import com.nowcoder.community.ops.application.result.ProjectionLagResult;
import com.nowcoder.community.ops.controller.dto.ProjectionLagResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ops/projections")
public class ProjectionOpsController {

    private final ProjectionGovernanceApplicationService projectionGovernanceApplicationService;

    public ProjectionOpsController(ProjectionGovernanceApplicationService projectionGovernanceApplicationService) {
        this.projectionGovernanceApplicationService = projectionGovernanceApplicationService;
    }

    @GetMapping("/lag")
    public Result<List<ProjectionLagResponse>> lag() {
        return Result.ok(projectionGovernanceApplicationService.listProjectionLag().stream()
                .map(this::toResponse)
                .toList());
    }

    private ProjectionLagResponse toResponse(ProjectionLagResult result) {
        return new ProjectionLagResponse(
                result.projection(),
                result.status(),
                result.count(),
                result.oldestAge()
        );
    }
}
