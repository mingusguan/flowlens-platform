package com.flowlens.admin.live.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LiveSessionStatsVO {

    private Long sessionId;

    private Long anchorId;

    private String anchorName;

    private String liveTitle;

    private String status;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Long durationSeconds;

    private Long peakViewers;

    private Long avgViewers;

    private Long totalViewers;

    private Long commentCount;

    private Long activeUserCount;

    private Double commentsPerUser;

    private Long likeCount;

    private Long followCount;

    private Long memberCount;

    private Integer highInteractionCount;

    private Integer lowInteractionCount;

    private Integer coldSegmentCount;
}
