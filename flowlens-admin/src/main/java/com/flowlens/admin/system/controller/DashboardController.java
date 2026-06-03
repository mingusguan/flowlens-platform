package com.flowlens.admin.system.controller;

import com.flowlens.admin.common.ApiResponse;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        return ApiResponse.ok(Map.of(
            "cards", List.of(
                Map.of("label", "监控账号", "value", 128, "trend", 12.5),
                Map.of("label", "今日播放", "value", 3862400, "trend", 18.2),
                Map.of("label", "互动增量", "value", 94280, "trend", 9.6),
                Map.of("label", "异常告警", "value", 7, "trend", -3.1)
            ),
            "trend", List.of(
                Map.of("day", "周一", "play", 82, "like", 35),
                Map.of("day", "周二", "play", 95, "like", 42),
                Map.of("day", "周三", "play", 116, "like", 51),
                Map.of("day", "周四", "play", 142, "like", 63),
                Map.of("day", "周五", "play", 168, "like", 74),
                Map.of("day", "周六", "play", 196, "like", 89),
                Map.of("day", "周日", "play", 221, "like", 96)
            )
        ));
    }
}
