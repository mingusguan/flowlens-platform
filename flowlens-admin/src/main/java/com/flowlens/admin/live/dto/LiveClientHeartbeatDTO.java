package com.flowlens.admin.live.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LiveClientHeartbeatDTO {

    @NotNull(message = "不能为空")
    private Long anchorId;

    @NotBlank(message = "不能为空")
    private String reportToken;

    private String liveId;

    private String roomId;

    private String liveTitle;

    private String liveStatus;

    private String clientInstanceId;

    private String clientVersion;
}
