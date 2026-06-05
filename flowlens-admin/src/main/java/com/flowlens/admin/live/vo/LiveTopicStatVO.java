package com.flowlens.admin.live.vo;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LiveTopicStatVO {

    private String keyword;

    private String category;

    private Long hitCount;

    private Long userCount;

    private LocalDateTime firstTime;

    private LocalDateTime lastTime;

    private List<String> sampleComments;
}
