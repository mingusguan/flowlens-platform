package com.flowlens.admin.system.vo;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MenuVO {

    private Long id;

    private Long parentId;

    private String menuName;

    private String menuType;

    private String path;

    private String component;

    private String permission;

    private String icon;

    private Integer sortOrder;

    private Integer visible;

    @Builder.Default
    private List<MenuVO> children = new ArrayList<>();
}
