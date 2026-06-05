package com.flowlens.admin.live.vo;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LiveTimelineBucketVO {

    private LocalDateTime minuteTime;

    private Long commentCount;

    private Long activeUserCount;

    private Long currentViewers;

    private Long viewerDelta;

    private List<String> topKeywords;

    private String emotionSummary;

    private List<String> segmentTags;
}
