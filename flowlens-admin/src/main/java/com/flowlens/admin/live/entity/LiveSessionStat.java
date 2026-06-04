package com.flowlens.admin.live.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("live_session_stat")
public class LiveSessionStat {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private Long anchorId;

    private Long commentCount;

    private Long likeCount;

    private Long giftCount;

    private Long giftValue;

    private Long viewerCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public static LiveSessionStat fromEvent(LiveEvent event) {
        LocalDateTime now = LocalDateTime.now();
        LiveSessionStat stat = new LiveSessionStat();
        stat.sessionId = event.getSessionId();
        stat.anchorId = event.getAnchorId();
        stat.commentCount = LiveEvent.TYPE_COMMENT.equals(event.getEventType()) ? 1L : 0L;
        stat.likeCount = LiveEvent.TYPE_LIKE.equals(event.getEventType()) ? positive(event.getLikeCount()) : 0L;
        stat.giftCount = LiveEvent.TYPE_GIFT.equals(event.getEventType()) ? positive(event.getGiftCount()) : 0L;
        stat.giftValue = LiveEvent.TYPE_GIFT.equals(event.getEventType()) ? positive(event.getGiftValue()) : 0L;
        stat.viewerCount = LiveEvent.TYPE_ROOM_STATS.equals(event.getEventType()) ? positive(event.getViewerCount()) : 0L;
        stat.createTime = now;
        stat.updateTime = now;
        return stat;
    }

    private static long positive(Long value) {
        return value == null ? 0L : Math.max(value, 0L);
    }

    private static long positive(Integer value) {
        return value == null ? 0L : Math.max(value.longValue(), 0L);
    }
}
