package com.flowlens.admin.live.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LiveGiftRankVO {

    private String userId;

    private String douyinAccount;

    private String nickname;

    private Long giftValue;

    private Integer giftCount;

    private Integer giftEventCount;
}
