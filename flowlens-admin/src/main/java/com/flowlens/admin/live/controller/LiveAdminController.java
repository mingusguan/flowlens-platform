package com.flowlens.admin.live.controller;

import com.flowlens.admin.common.ApiResponse;
import com.flowlens.admin.live.dto.LiveAnchorSaveDTO;
import com.flowlens.admin.live.dto.LiveSessionStartDTO;
import com.flowlens.admin.live.service.LiveDutyService;
import com.flowlens.admin.live.vo.LiveAnchorVO;
import com.flowlens.admin.live.vo.LiveCollectorTaskVO;
import com.flowlens.admin.live.vo.LiveSessionVO;
import com.flowlens.admin.live.vo.LiveSummaryVO;
import com.flowlens.admin.security.PermissionRequired;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/live")
public class LiveAdminController {

    private final LiveDutyService liveDutyService;

    @GetMapping("/anchors")
    @PermissionRequired("live:anchor:list")
    public ApiResponse<List<LiveAnchorVO>> listAnchors(@RequestParam(name = "keyword", required = false) String keyword) {
        return ApiResponse.ok(liveDutyService.listAnchors(keyword));
    }

    @PostMapping("/anchors")
    @PermissionRequired("live:anchor:save")
    public ApiResponse<LiveAnchorVO> saveAnchor(@Valid @RequestBody LiveAnchorSaveDTO dto) {
        return ApiResponse.ok(liveDutyService.saveAnchor(dto));
    }

    @DeleteMapping("/anchors/{id}")
    @PermissionRequired("live:anchor:delete")
    public ApiResponse<Void> deleteAnchor(@PathVariable("id") Long id) {
        liveDutyService.deleteAnchor(id);
        return ApiResponse.ok();
    }

    @PostMapping("/anchors/{anchorId}/sessions")
    @PermissionRequired("live:session:control")
    public ApiResponse<LiveSessionVO> startSession(@PathVariable("anchorId") Long anchorId, @RequestBody LiveSessionStartDTO dto) {
        return ApiResponse.ok(liveDutyService.startSession(anchorId, dto));
    }

    @PostMapping("/sessions/{sessionId}/end")
    @PermissionRequired("live:session:control")
    public ApiResponse<Void> endSession(@PathVariable("sessionId") Long sessionId) {
        liveDutyService.endSession(sessionId);
        return ApiResponse.ok();
    }

    @GetMapping("/sessions")
    @PermissionRequired("live:session:list")
    public ApiResponse<List<LiveSessionVO>> listSessions(@RequestParam(name = "anchorId", required = false) Long anchorId,
                                                         @RequestParam(name = "status", required = false) String status) {
        return ApiResponse.ok(liveDutyService.listSessions(anchorId, status));
    }

    @GetMapping("/sessions/{sessionId}/summary")
    @PermissionRequired("live:session:list")
    public ApiResponse<LiveSummaryVO> summary(@PathVariable("sessionId") Long sessionId) {
        return ApiResponse.ok(liveDutyService.getSummary(sessionId));
    }

    @GetMapping("/sessions/{sessionId}/collector-tasks")
    @PermissionRequired("live:session:list")
    public ApiResponse<List<LiveCollectorTaskVO>> collectorTasks(@PathVariable("sessionId") Long sessionId) {
        return ApiResponse.ok(liveDutyService.listCollectorTasks(sessionId));
    }
}
