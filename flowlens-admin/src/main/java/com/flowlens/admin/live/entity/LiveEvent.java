package com.flowlens.admin.live.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.flowlens.admin.common.BusinessException;
import com.flowlens.admin.live.dto.LiveEventReportDTO;
import java.time.LocalDateTime;
import lombok.Data;
import org.springframework.util.StringUtils;

@Data
@TableName("live_event")
public class LiveEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private Long anchorId;

    private String roomId;

    private String liveId;

    private String msgId;

    private String eventType;

    private String source;

    private String userId;

    private String douyinAccount;

    private String nickname;

    private String content;

    private String giftId;

    private String giftName;

    private Integer giftCount;

    private Long giftValue;

    private Long likeCount;

    private LocalDateTime eventTime;

    private LocalDateTime createTime;

    public static final String TYPE_GIFT = "GIFT";

    public static final String TYPE_COMMENT = "COMMENT";

    public static final String TYPE_LIKE = "LIKE";

    public static final String TYPE_LIVE_END = "LIVE_END";

    public static final String TYPE_MEMBER = "MEMBER";

    public static final String TYPE_FOLLOW = "FOLLOW";

    public static final String SOURCE_CLIENT = "CLIENT";

    public static final String SOURCE_CLOUD = "CLOUD";

    public static LiveEvent fromReport(LiveSession session, LiveEventReportDTO dto, String source) {
        return fromReport(session, dto, source, null);
    }

    public static LiveEvent fromReport(LiveSession session, LiveEventReportDTO dto, String source, LocalDateTime defaultEventTime) {
        LiveEvent event = new LiveEvent();
        event.sessionId = session.getId();
        event.anchorId = session.getAnchorId();
        event.roomId = firstText(dto.getRoomId(), session.getRoomId());
        event.liveId = firstText(dto.getLiveId(), session.getLiveId());
        event.msgId = cleanNullable(dto.getMsgId());
        event.eventType = normalizeType(dto.getEventType());
        event.source = normalizeSource(source);
        event.userId = cleanNullable(dto.getUserId());
        event.douyinAccount = cleanNullable(dto.getDouyinAccount());
        event.nickname = cleanNullable(dto.getNickname());
        event.content = cleanNullable(dto.getContent());
        event.giftId = cleanNullable(dto.getGiftId());
        event.giftName = cleanNullable(dto.getGiftName());
        event.giftCount = dto.getGiftCount() == null ? 0 : Math.max(dto.getGiftCount(), 0);
        event.giftValue = dto.getGiftValue() == null ? 0L : Math.max(dto.getGiftValue(), 0L);
        event.likeCount = dto.getLikeCount() == null ? 0L : Math.max(dto.getLikeCount(), 0L);
        event.eventTime = dto.getEventTime() == null ? firstTime(defaultEventTime, LocalDateTime.now()) : dto.getEventTime();
        event.createTime = LocalDateTime.now();
        return event;
    }

    public boolean isEndEvent() {
        return TYPE_LIVE_END.equals(eventType);
    }

    private static String normalizeType(String type) {
        if (!StringUtils.hasText(type)) {
            throw new BusinessException("事件类型不能为空");
        }
        String normalized = type.trim().toUpperCase();
        if (!TYPE_GIFT.equals(normalized)
            && !TYPE_COMMENT.equals(normalized)
            && !TYPE_LIKE.equals(normalized)
            && !TYPE_LIVE_END.equals(normalized)
            && !TYPE_MEMBER.equals(normalized)
            && !TYPE_FOLLOW.equals(normalized)) {
            throw new BusinessException("事件类型不支持");
        }
        return normalized;
    }

    private static String normalizeSource(String source) {
        String normalized = StringUtils.hasText(source) ? source.trim().toUpperCase() : SOURCE_CLIENT;
        if (!SOURCE_CLIENT.equals(normalized) && !SOURCE_CLOUD.equals(normalized)) {
            throw new BusinessException("事件来源不合法");
        }
        return normalized;
    }

    private static String firstText(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first.trim();
        }
        return cleanNullable(second);
    }

    private static String cleanNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static LocalDateTime firstTime(LocalDateTime first, LocalDateTime second) {
        return first == null ? second : first;
    }
}
