package com.flowlens.admin.live.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LiveAnchorVO {

    private Long id;

    private String anchorName;

    private String douyinLiveId;

    private String reportToken;

    private Integer status;

    private Integer cloudCollectEnabled;

    private Integer clientOnline;

    private String clientInstanceId;

    private String clientVersion;

    private LocalDateTime clientLastHeartbeatTime;

    private Integer cloudCollecting;

    private LocalDateTime createTime;
}
