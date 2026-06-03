package com.flowlens.admin.live.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LiveAnchorSaveDTO {

    private Long id;

    @NotBlank(message = "不能为空")
    private String anchorName;

    private String douyinLiveId;

    private String roomId;

    private Integer status;

    private Integer cloudCollectEnabled;
}
