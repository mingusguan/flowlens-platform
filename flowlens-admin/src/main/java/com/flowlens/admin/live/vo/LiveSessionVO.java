package com.flowlens.admin.live.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LiveSessionVO {

    private Long id;

    private Long anchorId;

    private String anchorName;

    private String liveId;

    private String roomId;

    private String liveTitle;

    private String status;

    private String activeSource;

    private Long cloudTaskId;

    private LocalDateTime startTime;

    private LocalDateTime endTime;
}
