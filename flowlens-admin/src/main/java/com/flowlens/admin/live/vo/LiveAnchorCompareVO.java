package com.flowlens.admin.live.vo;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LiveAnchorCompareVO {

    private Long anchorId;

    private String anchorName;

    private Long sessionCount;

    private Long totalDurationSeconds;

    private Long avgPeakViewers;

    private Long avgViewers;

    private Long totalComments;

    private Long avgComments;

    private Long activeUserCount;

    private Double commentsPerViewer;

    private Double controversyRatio;

    private Long coldMinutes;

    private List<String> topTopics;
}
