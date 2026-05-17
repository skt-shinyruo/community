package com.nowcoder.community.runtime.controller;

import com.nowcoder.community.runtime.application.RuntimeConfigApplicationService;
import com.nowcoder.community.runtime.application.result.RuntimeConfigResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/runtime-config")
public class RuntimeConfigController {

    private final RuntimeConfigApplicationService applicationService;

    public RuntimeConfigController(RuntimeConfigApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping
    public ResponseEntity<RuntimeConfigResult> runtimeConfig() {
        return ResponseEntity.ok(applicationService.current());
    }
}
