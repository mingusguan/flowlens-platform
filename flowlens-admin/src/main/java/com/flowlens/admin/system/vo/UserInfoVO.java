package com.flowlens.admin.system.vo;

import java.util.Set;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserInfoVO {

    private Long id;

    private String username;

    private String nickname;

    private String email;

    private String mobile;

    private Integer status;

    private Set<String> roles;

    private Set<String> permissions;
}
