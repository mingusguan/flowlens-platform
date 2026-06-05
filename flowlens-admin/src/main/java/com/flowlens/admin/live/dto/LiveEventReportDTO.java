package com.flowlens.admin.live.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class LiveEventReportDTO {

    @NotNull(message = "不能为空")
    private Long anchorId;

    @NotBlank(message = "不能为空")
    private String reportToken;

    private String liveId;

    private String roomId;

    private String liveTitle;

    private String msgId;

    @NotBlank(message = "不能为空")
    private String eventType;

    private String userId;

    private String douyinAccount;

    private String nickname;

    private String content;

    private String giftId;

    private String giftName;

    private Integer giftCount;

    private Long giftValue;

    private Long likeCount;

    private Long viewerCount;

    @JsonDeserialize(using = FlexibleLocalDateTimeDeserializer.class)
    private LocalDateTime eventTime;

}
