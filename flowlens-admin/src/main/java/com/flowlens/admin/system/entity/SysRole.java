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
@TableName("sys_role")
public class SysRole {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String roleName;

    private String roleKey;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public static final long BUILT_IN_ADMIN_ROLE_ID = 1L;

    public static final int STATUS_ENABLED = 1;

    public static final int STATUS_DISABLED = 0;

    public static SysRole create(RoleProfileCommand command) {
        SysRole role = new SysRole();
        LocalDateTime now = LocalDateTime.now();
        role.roleName = requireText(command.getRoleName(), "角色名称不能为空");
        role.roleKey = requireText(command.getRoleKey(), "角色标识不能为空");
        role.status = normalizeStatus(command.getStatus());
        role.createTime = now;
        role.updateTime = now;
        return role;
    }

    public void update(RoleProfileCommand command) {
        this.roleName = requireText(command.getRoleName(), "角色名称不能为空");
        this.roleKey = requireText(command.getRoleKey(), "角色标识不能为空");
        this.status = normalizeStatus(command.getStatus());
        this.updateTime = LocalDateTime.now();
    }

    public void ensureCanDelete() {
        if (Long.valueOf(BUILT_IN_ADMIN_ROLE_ID).equals(id)) {
            throw new BusinessException("内置管理员角色不允许删除");
        }
    }

    private static String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(message);
        }
        return value.trim();
    }

    private static Integer normalizeStatus(Integer status) {
        // 未传状态时按启用处理，保证新增角色默认可以参与权限计算。
        if (status == null) {
            return STATUS_ENABLED;
        }
        if (!Integer.valueOf(STATUS_ENABLED).equals(status) && !Integer.valueOf(STATUS_DISABLED).equals(status)) {
            throw new BusinessException("角色状态不合法");
        }
        return status;
    }

    @Data
    @Builder
    public static class RoleProfileCommand {

        private String roleName;

        private String roleKey;

        private Integer status;
    }
}
