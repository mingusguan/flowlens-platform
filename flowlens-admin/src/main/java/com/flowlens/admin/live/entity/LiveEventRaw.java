package com.flowlens.admin.live.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("live_event_raw")
public class LiveEventRaw {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long eventId;

    private Long sessionId;

    private Long anchorId;

    private String rawPayload;

    private LocalDateTime createTime;

    public static LiveEventRaw fromEvent(LiveEvent event, String rawPayload) {
        LiveEventRaw raw = new LiveEventRaw();
        raw.eventId = event.getId();
        raw.sessionId = event.getSessionId();
        raw.anchorId = event.getAnchorId();
        raw.rawPayload = rawPayload;
        raw.createTime = LocalDateTime.now();
        return raw;
    }
}
