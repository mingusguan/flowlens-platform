package com.flowlens.admin.live.controller;

import com.flowlens.admin.common.ApiResponse;
import com.flowlens.admin.live.dto.LiveAnchorSaveDTO;
import com.flowlens.admin.live.dto.LiveCompareQueryDTO;
import com.flowlens.admin.live.dto.LiveSessionQueryDTO;
import com.flowlens.admin.live.service.LiveDutyService;
import com.flowlens.admin.live.service.LiveReviewService;
import com.flowlens.admin.live.vo.LiveActiveUserVO;
import com.flowlens.admin.live.vo.LiveAnchorVO;
import com.flowlens.admin.live.vo.LiveAnchorCompareVO;
import com.flowlens.admin.live.vo.LiveCollectorTaskVO;
import com.flowlens.admin.live.vo.LiveEmotionStatVO;
import com.flowlens.admin.live.vo.LiveReviewReportVO;
import com.flowlens.admin.live.vo.LiveSegmentVO;
import com.flowlens.admin.live.vo.LiveSessionCompareVO;
import com.flowlens.admin.live.vo.LiveSessionStatsVO;
import com.flowlens.admin.live.vo.LiveSessionVO;
import com.flowlens.admin.live.vo.LiveSummaryVO;
import com.flowlens.admin.live.vo.LiveTimelineBucketVO;
import com.flowlens.admin.live.vo.LiveTopicStatVO;
import com.flowlens.admin.security.PermissionRequired;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
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

    private final LiveReviewService liveReviewService;

    @GetMapping("/anchors")
    @PermissionRequired("live:anchor:list")
    public ApiResponse<List<LiveAnchorVO>> listAnchors(@RequestParam(name = "keyword", required = false) String keyword) {
        return ApiResponse.ok(liveDutyService.listAnchors(keyword));
    }

    @GetMapping("/anchors/{id}")
    @PermissionRequired("live:anchor:list")
    public ApiResponse<LiveAnchorVO> anchor(@PathVariable("id") Long id) {
        return ApiResponse.ok(liveDutyService.getAnchor(id));
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

    @PostMapping("/anchors/{id}/enable-monitor")
    @PermissionRequired("live:anchor:save")
    public ApiResponse<LiveAnchorVO> enableMonitor(@PathVariable("id") Long id) {
        return ApiResponse.ok(liveDutyService.changeAnchorCloudMonitor(id, true));
    }

    @PostMapping("/anchors/{id}/disable-monitor")
    @PermissionRequired("live:anchor:save")
    public ApiResponse<LiveAnchorVO> disableMonitor(@PathVariable("id") Long id) {
        return ApiResponse.ok(liveDutyService.changeAnchorCloudMonitor(id, false));
    }

    @GetMapping("/anchors/{id}/monitor-status")
    @PermissionRequired("live:anchor:list")
    public ApiResponse<LiveAnchorVO> monitorStatus(@PathVariable("id") Long id) {
        return ApiResponse.ok(liveDutyService.getAnchor(id));
    }

    @GetMapping("/anchors/{id}/sessions")
    @PermissionRequired("live:session:list")
    public ApiResponse<List<LiveSessionVO>> anchorSessions(@PathVariable("id") Long id) {
        return ApiResponse.ok(liveReviewService.listAnchorSessions(id));
    }

    @GetMapping("/sessions")
    @PermissionRequired("live:session:list")
    public ApiResponse<List<LiveSessionVO>> listSessions(@RequestParam(name = "anchorId", required = false) Long anchorId,
                                                         @RequestParam(name = "status", required = false) String status,
                                                         @RequestParam(name = "startTime", required = false) LocalDateTime startTime,
                                                         @RequestParam(name = "endTime", required = false) LocalDateTime endTime) {
        LiveSessionQueryDTO query = new LiveSessionQueryDTO();
        query.setAnchorId(anchorId);
        query.setStatus(status);
        query.setStartTime(startTime);
        query.setEndTime(endTime);
        return ApiResponse.ok(liveReviewService.listSessions(query));
    }

    @GetMapping("/sessions/{sessionId}")
    @PermissionRequired("live:session:list")
    public ApiResponse<LiveSessionVO> session(@PathVariable("sessionId") Long sessionId) {
        return ApiResponse.ok(liveReviewService.getSession(sessionId));
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

    @GetMapping("/sessions/{sessionId}/stats")
    @PermissionRequired("live:review:view")
    public ApiResponse<LiveSessionStatsVO> stats(@PathVariable("sessionId") Long sessionId) {
        return ApiResponse.ok(liveReviewService.getSessionStats(sessionId));
    }

    @GetMapping("/sessions/{sessionId}/timeline")
    @PermissionRequired("live:review:view")
    public ApiResponse<List<LiveTimelineBucketVO>> timeline(@PathVariable("sessionId") Long sessionId) {
        return ApiResponse.ok(liveReviewService.getTimeline(sessionId));
    }

    @GetMapping("/sessions/{sessionId}/topics")
    @PermissionRequired("live:review:view")
    public ApiResponse<List<LiveTopicStatVO>> topics(@PathVariable("sessionId") Long sessionId) {
        return ApiResponse.ok(liveReviewService.getTopics(sessionId));
    }

    @GetMapping("/sessions/{sessionId}/active-users")
    @PermissionRequired("live:review:view")
    public ApiResponse<List<LiveActiveUserVO>> activeUsers(@PathVariable("sessionId") Long sessionId) {
        return ApiResponse.ok(liveReviewService.getActiveUsers(sessionId));
    }

    @GetMapping("/sessions/{sessionId}/emotions")
    @PermissionRequired("live:review:view")
    public ApiResponse<List<LiveEmotionStatVO>> emotions(@PathVariable("sessionId") Long sessionId) {
        return ApiResponse.ok(liveReviewService.getEmotions(sessionId));
    }

    @GetMapping("/sessions/{sessionId}/segments")
    @PermissionRequired("live:review:view")
    public ApiResponse<List<LiveSegmentVO>> segments(@PathVariable("sessionId") Long sessionId) {
        return ApiResponse.ok(liveReviewService.getSegments(sessionId));
    }

    @PostMapping("/sessions/{sessionId}/generate-report")
    @PermissionRequired("live:review:view")
    public ApiResponse<LiveReviewReportVO> generateReport(@PathVariable("sessionId") Long sessionId) {
        return ApiResponse.ok(liveReviewService.generateReport(sessionId));
    }

    @GetMapping("/sessions/{sessionId}/report")
    @PermissionRequired("live:review:view")
    public ApiResponse<LiveReviewReportVO> report(@PathVariable("sessionId") Long sessionId) {
        return ApiResponse.ok(liveReviewService.getReport(sessionId));
    }

    @GetMapping("/compare/sessions")
    @PermissionRequired("live:compare:view")
    public ApiResponse<List<LiveSessionCompareVO>> compareSessions(@RequestParam(name = "anchorId", required = false) Long anchorId,
                                                                   @RequestParam(name = "anchorIds", required = false) List<Long> anchorIds,
                                                                   @RequestParam(name = "sessionIds", required = false) List<Long> sessionIds,
                                                                   @RequestParam(name = "startTime", required = false) LocalDateTime startTime,
                                                                   @RequestParam(name = "endTime", required = false) LocalDateTime endTime) {
        LiveCompareQueryDTO query = new LiveCompareQueryDTO();
        query.setAnchorId(anchorId);
        query.setAnchorIds(anchorIds);
        query.setSessionIds(sessionIds);
        query.setStartTime(startTime);
        query.setEndTime(endTime);
        return ApiResponse.ok(liveReviewService.compareSessions(query));
    }

    @GetMapping("/compare/anchors")
    @PermissionRequired("live:compare:view")
    public ApiResponse<List<LiveAnchorCompareVO>> compareAnchors(@RequestParam(name = "anchorIds", required = false) List<Long> anchorIds,
                                                                @RequestParam(name = "startTime", required = false) LocalDateTime startTime,
                                                                @RequestParam(name = "endTime", required = false) LocalDateTime endTime) {
        LiveCompareQueryDTO query = new LiveCompareQueryDTO();
        query.setAnchorIds(anchorIds);
        query.setStartTime(startTime);
        query.setEndTime(endTime);
        return ApiResponse.ok(liveReviewService.compareAnchors(query));
    }

}
