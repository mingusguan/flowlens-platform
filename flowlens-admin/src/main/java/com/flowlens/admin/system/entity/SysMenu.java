package com.flowlens.admin.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.flowlens.admin.common.BusinessException;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;
import org.springframework.util.StringUtils;

@Data
@TableName("sys_menu")
public class SysMenu {

    @TableId(type = IdType.AUTO)
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

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public static final long ROOT_PARENT_ID = 0L;

    public static final int DEFAULT_SORT_ORDER = 0;

    public static final int VISIBLE = 1;

    public static final int HIDDEN = 0;

    public static SysMenu create(MenuProfileCommand command) {
        SysMenu menu = new SysMenu();
        LocalDateTime now = LocalDateTime.now();
        menu.applyProfile(command);
        menu.createTime = now;
        menu.updateTime = now;
        return menu;
    }

    public void update(MenuProfileCommand command) {
        applyProfile(command);
        this.updateTime = LocalDateTime.now();
    }

    public boolean isRootMenu() {
        return parentId == null || Long.valueOf(ROOT_PARENT_ID).equals(parentId);
    }

    private void applyProfile(MenuProfileCommand command) {
        this.parentId = command.getParentId() == null ? ROOT_PARENT_ID : command.getParentId();
        this.menuName = requireText(command.getMenuName(), "菜单名称不能为空");
        this.menuType = requireText(command.getMenuType(), "菜单类型不能为空");
        this.path = cleanNullable(command.getPath());
        this.component = cleanNullable(command.getComponent());
        this.permission = cleanNullable(command.getPermission());
        this.icon = cleanNullable(command.getIcon());
        this.sortOrder = command.getSortOrder() == null ? DEFAULT_SORT_ORDER : command.getSortOrder();
        this.visible = normalizeVisible(command.getVisible());
    }

    private static String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(message);
        }
        return value.trim();
    }

    private static String cleanNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static Integer normalizeVisible(Integer visible) {
        // 未传显隐状态时默认展示，保证菜单保存后能立即进入权限树。
        if (visible == null) {
            return VISIBLE;
        }
        if (!Integer.valueOf(VISIBLE).equals(visible) && !Integer.valueOf(HIDDEN).equals(visible)) {
            throw new BusinessException("菜单显隐状态不合法");
        }
        return visible;
    }

    @Data
    @Builder
    public static class MenuProfileCommand {

        private Long parentId;

        private String menuName;

        private String menuType;

        private String path;

        private String component;

        private String permission;

        private String icon;

        private Integer sortOrder;

        private Integer visible;
    }
}
