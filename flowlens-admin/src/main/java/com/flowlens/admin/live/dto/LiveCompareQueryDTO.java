package com.flowlens.admin.live.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class LiveCompareQueryDTO {

    private Long anchorId;

    private List<Long> anchorIds;

    private List<Long> sessionIds;

    @JsonDeserialize(using = FlexibleLocalDateTimeDeserializer.class)
    private LocalDateTime startTime;

    @JsonDeserialize(using = FlexibleLocalDateTimeDeserializer.class)
    private LocalDateTime endTime;
}
