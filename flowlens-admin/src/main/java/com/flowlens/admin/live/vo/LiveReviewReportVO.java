package com.flowlens.admin.live.vo;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LiveReviewReportVO {

    private LiveSessionStatsVO stats;

    private List<LiveTimelineBucketVO> timeline;

    private List<LiveTopicStatVO> topTopics;

    private List<LiveActiveUserVO> activeUsers;

    private List<LiveEmotionStatVO> emotions;

    private List<LiveSegmentVO> coldSegments;

    private List<String> suggestions;

    private String summary;
}
