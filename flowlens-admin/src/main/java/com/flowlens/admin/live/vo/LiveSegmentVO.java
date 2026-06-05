package com.flowlens.admin.live.vo;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LiveSegmentVO {

    private String segmentType;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Long durationMinutes;

    private Long commentCount;

    private Long activeUserCount;

    private Long viewerDelta;

    private List<String> keywords;

    private List<String> sampleComments;

    private String suggestion;
}
