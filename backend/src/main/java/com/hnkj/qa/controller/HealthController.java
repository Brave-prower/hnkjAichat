package com.hnkj.qa.controller;

import com.hnkj.qa.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 健康检查接口，用于前后端联调与部署探活。
 */
@Tag(name = "系统", description = "健康检查")
@RestController
@RequestMapping("/system")
public class HealthController {

    @Operation(summary = "服务健康检查")
    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.ok(Map.of(
                "status", "UP",
                "service", "campus-qa",
                "time", LocalDateTime.now().toString()
        ));
    }
}
