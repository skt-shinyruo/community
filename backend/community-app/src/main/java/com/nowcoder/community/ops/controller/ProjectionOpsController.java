package com.nowcoder.community.ops.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.ops.application.ProjectionGovernanceApplicationService;
import com.nowcoder.community.ops.application.result.ProjectionLagResult;
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
    public Result<List<ProjectionLagResult>> lag() {
        return Result.ok(projectionGovernanceApplicationService.listProjectionLag());
    }
}
