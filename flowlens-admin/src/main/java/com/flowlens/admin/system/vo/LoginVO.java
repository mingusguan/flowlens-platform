package com.flowlens.admin.system.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginVO {

    private String token;

    private UserInfoVO user;
}
