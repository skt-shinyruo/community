package com.nowcoder.community.ops.application;

import com.nowcoder.community.ops.application.result.ProjectionLagResult;

import java.util.List;

public interface ProjectionLagPort {

    List<ProjectionLagResult> listProjectionLag();
}
