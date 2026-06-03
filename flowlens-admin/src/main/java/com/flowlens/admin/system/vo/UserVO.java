package com.flowlens.admin.system.vo;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserVO {

    private Long id;

    private String username;

    private String nickname;

    private String email;

    private String mobile;

    private Integer status;

    private List<Long> roleIds;

    private LocalDateTime createTime;
}
