package com.flowlens.admin.system.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.Data;

@Data
public class RoleSaveDTO {

    private Long id;

    @NotBlank(message = "不能为空")
    private String roleName;

    @NotBlank(message = "不能为空")
    private String roleKey;

    private Integer status;

    private List<Long> menuIds;
}
