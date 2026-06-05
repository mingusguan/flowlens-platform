package com.flowlens.admin.live.vo;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LiveSessionCompareVO {

    private Long sessionId;

    private Long anchorId;

    private String anchorName;

    private String liveTitle;

    private LocalDateTime startTime;

    private Long durationSeconds;

    private Long peakViewers;

    private Long avgViewers;

    private Long commentCount;

    private Long activeUserCount;

    private Double commentsPerViewer;

    private Long oldViewerCount;

    private Long coldMinutes;

    private Double controversyRatio;

    private List<String> topTopics;
}
