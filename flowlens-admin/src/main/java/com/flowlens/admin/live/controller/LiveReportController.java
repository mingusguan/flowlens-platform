package com.flowlens.admin.live.controller;

import com.flowlens.admin.common.ApiResponse;
import com.flowlens.admin.live.dto.LiveEventReportDTO;
import com.flowlens.admin.live.service.LiveDutyService;
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

    @PostMapping("/cloud/events")
    public ApiResponse<Void> cloudEvent(@Valid @RequestBody LiveEventReportDTO dto) {
        liveDutyService.reportCloudEvent(dto);
        return ApiResponse.ok();
    }
}
