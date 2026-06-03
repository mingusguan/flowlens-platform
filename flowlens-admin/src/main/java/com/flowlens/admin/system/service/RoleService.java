package com.flowlens.admin.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.flowlens.admin.common.BusinessException;
import com.flowlens.admin.system.dto.RoleSaveDTO;
import com.flowlens.admin.system.entity.SysRole;
import com.flowlens.admin.system.entity.SysRoleMenu;
import com.flowlens.admin.system.entity.SysUserRole;
import com.flowlens.admin.system.mapper.SysRoleMapper;
import com.flowlens.admin.system.mapper.SysRoleMenuMapper;
import com.flowlens.admin.system.mapper.SysUserRoleMapper;
import com.flowlens.admin.system.vo.RoleVO;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final SysRoleMapper roleMapper;

    private final SysRoleMenuMapper roleMenuMapper;

    private final SysUserRoleMapper userRoleMapper;

    public List<RoleVO> listRoles(String keyword) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<SysRole>()
            .orderByDesc(SysRole::getCreateTime);
        if (StringUtils.hasText(keyword)) {
            // 关键字同时匹配角色名称和角色标识，便于后台按中文名或权限标识检索。
            wrapper.and(item -> item.like(SysRole::getRoleName, keyword)
                .or()
                .like(SysRole::getRoleKey, keyword));
        }
        List<SysRole> roles = roleMapper.selectList(wrapper);
        if (roles.isEmpty()) {
            return Collections.emptyList();
        }
        // 列表页不使用 join，批量查角色菜单绑定后在代码中组装。
        Map<Long, List<Long>> menuIdsByRoleId = selectMenuIdsByRoleIds(roles.stream()
            .map(SysRole::getId)
            .toList());
        return roles.stream()
            .map(role -> toVO(role, menuIdsByRoleId.getOrDefault(role.getId(), Collections.emptyList())))
            .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public RoleVO saveRole(RoleSaveDTO dto) {
        SysRole role = dto.getId() == null ? createRole(dto) : updateRole(dto);
        // 角色主表和菜单权限绑定在同一事务内更新，避免权限树出现半更新状态。
        List<Long> menuIds = replaceRoleMenus(role.getId(), dto.getMenuIds());
        return toVO(role, menuIds);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long id) {
        SysRole role = requireRole(id);
        role.ensureCanDelete();
        // 删除角色前先确认没有用户绑定，避免用户保留无效角色关系。
        Long count = userRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, id));
        if (count > 0) {
            throw new BusinessException("角色已绑定用户，不能删除");
        }
        roleMapper.deleteById(id);
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, id));
    }

    private SysRole createRole(RoleSaveDTO dto) {
        ensureRoleKeyUnique(dto.getRoleKey(), null);
        SysRole role = SysRole.create(SysRole.RoleProfileCommand.builder()
            .roleName(dto.getRoleName())
            .roleKey(dto.getRoleKey())
            .status(dto.getStatus())
            .build());
        roleMapper.insert(role);
        return role;
    }

    private SysRole updateRole(RoleSaveDTO dto) {
        SysRole role = requireRole(dto.getId());
        ensureRoleKeyUnique(dto.getRoleKey(), dto.getId());
        role.update(SysRole.RoleProfileCommand.builder()
            .roleName(dto.getRoleName())
            .roleKey(dto.getRoleKey())
            .status(dto.getStatus())
            .build());
        roleMapper.updateById(role);
        return role;
    }

    private SysRole requireRole(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException("角色不存在");
        }
        return role;
    }

    private List<Long> replaceRoleMenus(Long roleId, List<Long> menuIds) {
        // 先清理旧菜单权限，再批量写入本次提交的权限范围。
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));
        if (menuIds == null || menuIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> normalizedMenuIds = menuIds.stream()
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        // 菜单 ID 去重后批量保存，避免循环单条写库。
        List<SysRoleMenu> bindings = normalizedMenuIds.stream()
            .map(menuId -> SysRoleMenu.bind(roleId, menuId))
            .toList();
        if (!bindings.isEmpty()) {
            Db.saveBatch(bindings);
        }
        return normalizedMenuIds;
    }

    private void ensureRoleKeyUnique(String roleKey, Long excludedId) {
        SysRole exists = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleKey, roleKey));
        if (exists != null && !Objects.equals(exists.getId(), excludedId)) {
            throw new BusinessException("角色标识已存在");
        }
    }

    private Map<Long, List<Long>> selectMenuIdsByRoleIds(Collection<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return roleMenuMapper.selectList(new LambdaQueryWrapper<SysRoleMenu>()
                .in(SysRoleMenu::getRoleId, roleIds))
            .stream()
            .filter(binding -> binding.getRoleId() != null && binding.getMenuId() != null)
            .collect(Collectors.groupingBy(
                SysRoleMenu::getRoleId,
                Collectors.mapping(SysRoleMenu::getMenuId, Collectors.toList())
            ));
    }

    private RoleVO toVO(SysRole role, List<Long> menuIds) {
        return RoleVO.builder()
            .id(role.getId())
            .roleName(role.getRoleName())
            .roleKey(role.getRoleKey())
            .status(role.getStatus())
            .menuIds(menuIds == null ? Collections.emptyList() : menuIds)
            .createTime(role.getCreateTime())
            .build();
    }
}
