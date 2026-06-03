package com.flowlens.admin.live.dto;

import lombok.Data;

@Data
public class LiveSessionStartDTO {

    private String liveId;

    private String roomId;

    private String liveTitle;
}
