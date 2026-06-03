package com.flowlens.admin.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.flowlens.admin.common.BusinessException;
import com.flowlens.admin.security.CurrentUser;
import com.flowlens.admin.security.JwtTokenProvider;
import com.flowlens.admin.system.dto.LoginDTO;
import com.flowlens.admin.system.entity.SysMenu;
import com.flowlens.admin.system.entity.SysRole;
import com.flowlens.admin.system.entity.SysRoleMenu;
import com.flowlens.admin.system.entity.SysUser;
import com.flowlens.admin.system.entity.SysUserRole;
import com.flowlens.admin.system.mapper.SysMenuMapper;
import com.flowlens.admin.system.mapper.SysRoleMapper;
import com.flowlens.admin.system.mapper.SysRoleMenuMapper;
import com.flowlens.admin.system.mapper.SysUserMapper;
import com.flowlens.admin.system.mapper.SysUserRoleMapper;
import com.flowlens.admin.system.vo.LoginVO;
import com.flowlens.admin.system.vo.MenuVO;
import com.flowlens.admin.system.vo.UserInfoVO;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper userMapper;

    private final SysRoleMapper roleMapper;

    private final SysMenuMapper menuMapper;

    private final SysUserRoleMapper userRoleMapper;

    private final SysRoleMenuMapper roleMenuMapper;

    private final PasswordEncoder passwordEncoder;

    private final JwtTokenProvider jwtTokenProvider;

    public LoginVO login(LoginDTO dto) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getUsername, dto.getUsername()));
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        // 登录前校验账号状态，停用账号不能签发新的后台令牌。
        user.ensureCanLogin();
        UserInfoVO userInfo = buildUserInfo(user);
        return LoginVO.builder()
            .token(jwtTokenProvider.createToken(user.getId(), user.getUsername()))
            .user(userInfo)
            .build();
    }

    public UserInfoVO currentUser(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(401, "登录状态已失效");
        }
        return buildUserInfo(user);
    }

    public CurrentUser loadCurrentUser(Long userId) {
        UserInfoVO info = currentUser(userId);
        return new CurrentUser(info.getId(), info.getUsername(), info.getNickname(), info.getRoles(), info.getPermissions());
    }

    public List<MenuVO> userMenus(Long userId) {
        // 权限菜单按用户角色逐层查询，避免通过 join 直接拼装跨表结果。
        Set<Long> roleIds = selectUserRoleIds(userId);
        if (roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> menuIds = selectRoleMenuIds(roleIds);
        if (menuIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<SysMenu> menus = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
            .in(SysMenu::getId, menuIds)
            .eq(SysMenu::getVisible, 1)
            .orderByAsc(SysMenu::getSortOrder));
        return buildMenuTree(menus);
    }

    public UserInfoVO buildUserInfo(SysUser user) {
        Set<Long> roleIds = selectUserRoleIds(user.getId());
        // 仅启用角色参与权限计算，停用角色保留绑定但不授予权限。
        List<SysRole> roles = roleIds.isEmpty()
            ? Collections.emptyList()
            : roleMapper.selectByIds(roleIds).stream()
                .filter(role -> Integer.valueOf(1).equals(role.getStatus()))
                .toList();
        Set<String> roleKeys = roles.stream().map(SysRole::getRoleKey).collect(Collectors.toSet());
        Set<Long> enabledRoleIds = roles.stream().map(SysRole::getId).collect(Collectors.toSet());
        Set<String> permissions = selectPermissions(enabledRoleIds);
        return UserInfoVO.builder()
            .id(user.getId())
            .username(user.getUsername())
            .nickname(user.getNickname())
            .email(user.getEmail())
            .mobile(user.getMobile())
            .status(user.getStatus())
            .roles(roleKeys)
            .permissions(permissions)
            .build();
    }

    private Set<Long> selectUserRoleIds(Long userId) {
        // 用户角色绑定来自独立关系表，后续在代码中和角色、菜单数据组装。
        return userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId))
            .stream()
            .map(SysUserRole::getRoleId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }

    private Set<Long> selectRoleMenuIds(Set<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return Collections.emptySet();
        }
        // 批量查询角色菜单绑定，避免按角色循环查询权限。
        return roleMenuMapper.selectList(new LambdaQueryWrapper<SysRoleMenu>()
                .in(SysRoleMenu::getRoleId, roleIds))
            .stream()
            .map(SysRoleMenu::getMenuId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }

    private Set<String> selectPermissions(Set<Long> roleIds) {
        Set<Long> menuIds = selectRoleMenuIds(roleIds);
        if (menuIds.isEmpty()) {
            return Collections.emptySet();
        }
        // 只返回配置了权限标识的菜单或按钮，前端路由节点可没有 permission。
        return menuMapper.selectByIds(menuIds).stream()
            .map(SysMenu::getPermission)
            .filter(StringUtils::hasText)
            .collect(Collectors.toSet());
    }

    private List<MenuVO> buildMenuTree(List<SysMenu> menus) {
        // 菜单树按单表查询结果在内存中组装，避免递归 SQL 或多表 join。
        Map<Long, MenuVO> voMap = menus.stream()
            .map(this::toMenuVO)
            .collect(Collectors.toMap(MenuVO::getId, Function.identity(), (left, right) -> left));
        List<MenuVO> roots = voMap.values().stream()
            .filter(menu -> {
                Long parentId = menu.getParentId();
                return parentId == null || Long.valueOf(SysMenu.ROOT_PARENT_ID).equals(parentId) || !voMap.containsKey(parentId);
            })
            .sorted(Comparator.comparing(MenuVO::getSortOrder, Comparator.nullsLast(Integer::compareTo)))
            .toList();
        voMap.values().forEach(menu -> {
            Long parentId = menu.getParentId();
            if (parentId != null && !Long.valueOf(SysMenu.ROOT_PARENT_ID).equals(parentId) && voMap.containsKey(parentId)) {
                voMap.get(parentId).getChildren().add(menu);
            }
        });
        // 每个层级都按 sort_order 排序，保证权限菜单展示稳定。
        voMap.values().forEach(menu -> menu.getChildren().sort(Comparator.comparing(MenuVO::getSortOrder, Comparator.nullsLast(Integer::compareTo))));
        return roots;
    }

    private MenuVO toMenuVO(SysMenu menu) {
        return MenuVO.builder()
            .id(menu.getId())
            .parentId(menu.getParentId())
            .menuName(menu.getMenuName())
            .menuType(menu.getMenuType())
            .path(menu.getPath())
            .component(menu.getComponent())
            .permission(menu.getPermission())
            .icon(menu.getIcon())
            .sortOrder(menu.getSortOrder())
            .visible(menu.getVisible())
            .build();
    }
}
