package com.flowlens.admin.live.vo;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LiveEmotionStatVO {

    private String emotion;

    private Long count;

    private Double ratio;

    private LocalDateTime peakMinute;

    private List<String> sampleComments;
}
