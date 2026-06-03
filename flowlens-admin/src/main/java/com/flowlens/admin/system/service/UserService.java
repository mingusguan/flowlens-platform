package com.flowlens.admin.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.flowlens.admin.common.BusinessException;
import com.flowlens.admin.system.dto.UserSaveDTO;
import com.flowlens.admin.system.entity.SysUser;
import com.flowlens.admin.system.entity.SysUserRole;
import com.flowlens.admin.system.mapper.SysUserMapper;
import com.flowlens.admin.system.mapper.SysUserRoleMapper;
import com.flowlens.admin.system.vo.UserVO;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserMapper userMapper;

    private final SysUserRoleMapper userRoleMapper;

    private final PasswordEncoder passwordEncoder;

    public List<UserVO> listUsers(String keyword) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
            .orderByDesc(SysUser::getCreateTime);
        if (StringUtils.hasText(keyword)) {
            // 关键字同时匹配登录名和昵称，满足后台用户列表的模糊搜索习惯。
            wrapper.and(item -> item.like(SysUser::getUsername, keyword)
                .or()
                .like(SysUser::getNickname, keyword));
        }
        List<SysUser> users = userMapper.selectList(wrapper);
        if (users.isEmpty()) {
            return Collections.emptyList();
        }
        // 列表页不使用 join，先按用户批量查角色绑定，再在代码中组装响应对象。
        Map<Long, List<Long>> roleIdsByUserId = selectRoleIdsByUserIds(users.stream()
            .map(SysUser::getId)
            .toList());
        return users.stream()
            .map(user -> toVO(user, roleIdsByUserId.getOrDefault(user.getId(), Collections.emptyList())))
            .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public UserVO saveUser(UserSaveDTO dto) {
        SysUser user = dto.getId() == null ? createUser(dto) : updateUser(dto);
        // 新建和编辑共用同一事务，保证用户主表和角色绑定一起提交。
        List<Long> roleIds = replaceUserRoles(user.getId(), dto.getRoleIds());
        return toVO(user, roleIds);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long id) {
        SysUser user = requireUser(id);
        user.ensureCanDelete();
        userMapper.deleteById(id);
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        SysUser user = requireUser(id);
        user.ensureCanChangeStatus(status);
        user.changeStatus(status);
        userMapper.updateById(user);
    }

    private SysUser createUser(UserSaveDTO dto) {
        ensureUsernameUnique(dto.getUsername(), null);
        SysUser user = SysUser.create(SysUser.UserCreateCommand.builder()
            .username(dto.getUsername())
            .rawPassword(dto.getPassword())
            .nickname(dto.getNickname())
            .email(dto.getEmail())
            .mobile(dto.getMobile())
            .status(dto.getStatus())
            .build(), passwordEncoder);
        userMapper.insert(user);
        return user;
    }

    private SysUser updateUser(UserSaveDTO dto) {
        SysUser user = requireUser(dto.getId());
        ensureUsernameUnique(dto.getUsername(), dto.getId());
        user.updateProfile(SysUser.UserProfileCommand.builder()
            .username(dto.getUsername())
            .nickname(dto.getNickname())
            .email(dto.getEmail())
            .mobile(dto.getMobile())
            .status(dto.getStatus())
            .build());
        if (StringUtils.hasText(dto.getPassword())) {
            user.changePassword(dto.getPassword(), passwordEncoder);
        }
        userMapper.updateById(user);
        return user;
    }

    private SysUser requireUser(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }

    private List<Long> replaceUserRoles(Long userId, List<Long> roleIds) {
        // 先清理旧绑定再批量写入新绑定，避免编辑用户后残留历史角色。
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        if (roleIds == null || roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> normalizedRoleIds = roleIds.stream()
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        // 角色 ID 去重后批量保存，避免循环单条写库。
        List<SysUserRole> bindings = normalizedRoleIds.stream()
            .map(roleId -> SysUserRole.bind(userId, roleId))
            .toList();
        if (!bindings.isEmpty()) {
            Db.saveBatch(bindings);
        }
        return normalizedRoleIds;
    }

    private void ensureUsernameUnique(String username, Long excludedId) {
        SysUser exists = userMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (exists != null && !Objects.equals(exists.getId(), excludedId)) {
            throw new BusinessException("用户名已存在");
        }
    }

    private Map<Long, List<Long>> selectRoleIdsByUserIds(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .in(SysUserRole::getUserId, userIds))
            .stream()
            .filter(binding -> binding.getUserId() != null && binding.getRoleId() != null)
            .collect(Collectors.groupingBy(
                SysUserRole::getUserId,
                Collectors.mapping(SysUserRole::getRoleId, Collectors.toList())
            ));
    }

    private UserVO toVO(SysUser user, List<Long> roleIds) {
        return UserVO.builder()
            .id(user.getId())
            .username(user.getUsername())
            .nickname(user.getNickname())
            .email(user.getEmail())
            .mobile(user.getMobile())
            .status(user.getStatus())
            .roleIds(roleIds == null ? Collections.emptyList() : roleIds)
            .createTime(user.getCreateTime())
            .build();
    }
}
