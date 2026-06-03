package com.flowlens.admin.system.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableName;
import com.flowlens.admin.common.BusinessException;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;

@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String password;

    private String nickname;

    private String email;

    private String mobile;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableField(exist = false)
    public static final long BUILT_IN_ADMIN_ID = 1L;

    @TableField(exist = false)
    public static final int STATUS_ENABLED = 1;

    @TableField(exist = false)
    public static final int STATUS_DISABLED = 0;

    public static SysUser create(UserCreateCommand command, PasswordEncoder passwordEncoder) {
        String username = requireText(command.getUsername(), "用户名不能为空");
        if (!StringUtils.hasText(command.getRawPassword())) {
            throw new BusinessException("初始密码不能为空");
        }
        SysUser user = new SysUser();
        LocalDateTime now = LocalDateTime.now();
        user.username = username;
        user.password = passwordEncoder.encode(command.getRawPassword());
        user.nickname = resolveNickname(command.getNickname(), username);
        user.email = cleanNullable(command.getEmail());
        user.mobile = cleanNullable(command.getMobile());
        user.status = normalizeStatus(command.getStatus());
        user.createTime = now;
        user.updateTime = now;
        return user;
    }

    public void updateProfile(UserProfileCommand command) {
        this.username = requireText(command.getUsername(), "用户名不能为空");
        this.nickname = requireText(command.getNickname(), "昵称不能为空");
        this.email = cleanNullable(command.getEmail());
        this.mobile = cleanNullable(command.getMobile());
        this.status = normalizeStatus(command.getStatus());
        this.updateTime = LocalDateTime.now();
    }

    public void changePassword(String rawPassword, PasswordEncoder passwordEncoder) {
        if (!StringUtils.hasText(rawPassword)) {
            throw new BusinessException("新密码不能为空");
        }
        this.password = passwordEncoder.encode(rawPassword);
        this.updateTime = LocalDateTime.now();
    }

    public void ensureCanLogin() {
        if (!Integer.valueOf(STATUS_ENABLED).equals(status)) {
            throw new BusinessException(401, "账号已停用");
        }
    }

    public void changeStatus(Integer nextStatus) {
        this.status = normalizeStatus(nextStatus);
        this.updateTime = LocalDateTime.now();
    }

    public void ensureCanDelete() {
        if (isBuiltInAdmin()) {
            throw new BusinessException("内置管理员不允许删除");
        }
    }

    public void ensureCanChangeStatus(Integer nextStatus) {
        if (isBuiltInAdmin() && Integer.valueOf(STATUS_DISABLED).equals(nextStatus)) {
            throw new BusinessException("内置管理员不允许停用");
        }
    }

    public boolean isBuiltInAdmin() {
        return Long.valueOf(BUILT_IN_ADMIN_ID).equals(id);
    }

    private static String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(message);
        }
        return value.trim();
    }

    private static String resolveNickname(String nickname, String username) {
        return StringUtils.hasText(nickname) ? nickname.trim() : username;
    }

    private static String cleanNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static Integer normalizeStatus(Integer status) {
        // 未传状态时按启用处理，避免新建或编辑后账号进入不可登录的半初始化状态。
        if (status == null) {
            return STATUS_ENABLED;
        }
        if (!Integer.valueOf(STATUS_ENABLED).equals(status) && !Integer.valueOf(STATUS_DISABLED).equals(status)) {
            throw new BusinessException("用户状态不合法");
        }
        return status;
    }

    @Data
    @Builder
    public static class UserCreateCommand {

        private String username;

        private String rawPassword;

        private String nickname;

        private String email;

        private String mobile;

        private Integer status;
    }

    @Data
    @Builder
    public static class UserProfileCommand {

        private String username;

        private String nickname;

        private String email;

        private String mobile;

        private Integer status;
    }
}
