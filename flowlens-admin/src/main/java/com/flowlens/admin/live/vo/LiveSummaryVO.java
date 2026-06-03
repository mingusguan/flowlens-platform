package com.flowlens.admin.live.vo;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LiveSummaryVO {

    private Long sessionId;

    private Long anchorId;

    private String anchorName;

    private String liveId;

    private String roomId;

    private String status;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Long commentCount;

    private Long likeCount;

    private Integer giftCount;

    private Long giftValue;

    private List<LiveGiftRankVO> giftRank;
}
