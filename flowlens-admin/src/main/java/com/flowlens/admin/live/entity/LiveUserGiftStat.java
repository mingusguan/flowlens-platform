package com.flowlens.admin.live.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("live_user_gift_stat")
public class LiveUserGiftStat {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private Long anchorId;

    private String userKey;

    private String userId;

    private String douyinAccount;

    private String nickname;

    private Long giftCount;

    private Long giftValue;

    private Long giftEventCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public static LiveUserGiftStat fromGiftEvent(LiveEvent event, String userKey) {
        LocalDateTime now = LocalDateTime.now();
        LiveUserGiftStat stat = new LiveUserGiftStat();
        stat.sessionId = event.getSessionId();
        stat.anchorId = event.getAnchorId();
        stat.userKey = userKey;
        stat.userId = event.getUserId();
        stat.douyinAccount = event.getDouyinAccount();
        stat.nickname = event.getNickname();
        stat.giftCount = positive(event.getGiftCount());
        stat.giftValue = positive(event.getGiftValue());
        stat.giftEventCount = 1L;
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
