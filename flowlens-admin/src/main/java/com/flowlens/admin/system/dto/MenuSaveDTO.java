package com.flowlens.admin.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MenuSaveDTO {

    private Long id;

    private Long parentId;

    @NotBlank(message = "不能为空")
    private String menuName;

    @NotBlank(message = "不能为空")
    private String menuType;

    private String path;

    private String component;

    private String permission;

    private String icon;

    private Integer sortOrder;

    private Integer visible;
}
