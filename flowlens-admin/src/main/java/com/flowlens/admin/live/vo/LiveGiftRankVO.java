package com.flowlens.admin.live.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LiveGiftRankVO {

    /**
     * 礼物榜渲染键，脱敏昵称重复时也保持唯一。
     */
    private String rankKey;

    private String userId;

    private String douyinAccount;

    private String nickname;

    private Long giftValue;

    private Integer giftCount;

    private Integer giftEventCount;
}
