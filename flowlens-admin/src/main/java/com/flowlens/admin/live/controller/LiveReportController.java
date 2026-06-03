package com.flowlens.admin.live.controller;

import com.flowlens.admin.common.ApiResponse;
import com.flowlens.admin.live.dto.LiveClientHeartbeatDTO;
import com.flowlens.admin.live.dto.LiveEventReportDTO;
import com.flowlens.admin.live.service.LiveDutyService;
import com.flowlens.admin.live.vo.LiveSessionVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/live/report")
public class LiveReportController {

    private final LiveDutyService liveDutyService;

    @PostMapping("/client/heartbeat")
    public ApiResponse<LiveSessionVO> clientHeartbeat(@Valid @RequestBody LiveClientHeartbeatDTO dto) {
        return ApiResponse.ok(liveDutyService.clientHeartbeat(dto));
    }

    @PostMapping("/client/events")
    public ApiResponse<Void> clientEvent(@Valid @RequestBody LiveEventReportDTO dto) {
        liveDutyService.reportClientEvent(dto);
        return ApiResponse.ok();
    }

    @PostMapping("/cloud/events")
    public ApiResponse<Void> cloudEvent(@Valid @RequestBody LiveEventReportDTO dto) {
        liveDutyService.reportCloudEvent(dto);
        return ApiResponse.ok();
    }
}
