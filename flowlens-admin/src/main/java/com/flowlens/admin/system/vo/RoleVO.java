package com.flowlens.admin.system.vo;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RoleVO {

    private Long id;

    private String roleName;

    private String roleKey;

    private Integer status;

    private List<Long> menuIds;

    private LocalDateTime createTime;
}
