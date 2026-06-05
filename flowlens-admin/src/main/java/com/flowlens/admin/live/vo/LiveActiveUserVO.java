package com.flowlens.admin.live.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LiveActiveUserVO {

    private String userKey;

    private String userId;

    private String douyinAccount;

    private String nickname;

    private Long commentCount;

    private LocalDateTime firstCommentTime;

    private LocalDateTime lastCommentTime;

    private Long activeSeconds;

    private String userType;

    private Long historicalSessionCount;

    private Boolean oldViewer;

    private Boolean newActiveViewer;
}
