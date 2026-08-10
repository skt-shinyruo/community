package com.nowcoder.community.ops.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.ops.application.ProjectionLagQuery;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ops/projections")
public class ProjectionOpsController {

    private final ProjectionLagQuery projectionLagQuery;

    public ProjectionOpsController(ProjectionLagQuery projectionLagQuery) {
        this.projectionLagQuery = projectionLagQuery;
    }

    @GetMapping("/lag")
    public Result<List<ProjectionLagQuery.ProjectionLag>> lag() {
        return Result.ok(projectionLagQuery.listProjectionLag());
    }
}
