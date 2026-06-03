package com.flowlens.admin.live.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LiveCollectorTaskVO {

    private Long id;

    private Long sessionId;

    private Long anchorId;

    private String status;

    private Long processId;

    private String commandLine;

    private String lastError;

    private LocalDateTime startTime;

    private LocalDateTime stopTime;
}
