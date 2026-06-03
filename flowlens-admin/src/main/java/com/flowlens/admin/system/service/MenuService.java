package com.flowlens.admin.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.flowlens.admin.common.BusinessException;
import com.flowlens.admin.system.dto.MenuSaveDTO;
import com.flowlens.admin.system.entity.SysMenu;
import com.flowlens.admin.system.entity.SysRoleMenu;
import com.flowlens.admin.system.mapper.SysMenuMapper;
import com.flowlens.admin.system.mapper.SysRoleMenuMapper;
import com.flowlens.admin.system.vo.MenuVO;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MenuService {

    private final SysMenuMapper menuMapper;

    private final SysRoleMenuMapper roleMenuMapper;

    public List<MenuVO> listMenus() {
        List<SysMenu> menus = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
            .orderByAsc(SysMenu::getSortOrder));
        return buildMenuTree(menus);
    }

    @Transactional(rollbackFor = Exception.class)
    public MenuVO saveMenu(MenuSaveDTO dto) {
        SysMenu menu = dto.getId() == null ? createMenu(dto) : updateMenu(dto);
        return toVO(menu);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteMenu(Long id) {
        // 删除菜单前确认没有子菜单，避免权限树出现悬挂节点。
        Long childCount = menuMapper.selectCount(new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getParentId, id));
        if (childCount > 0) {
            throw new BusinessException("存在子菜单，不能删除");
        }
        menuMapper.deleteById(id);
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getMenuId, id));
    }

    private SysMenu createMenu(MenuSaveDTO dto) {
        SysMenu menu = SysMenu.create(toProfileCommand(dto));
        menuMapper.insert(menu);
        return menu;
    }

    private SysMenu updateMenu(MenuSaveDTO dto) {
        SysMenu menu = menuMapper.selectById(dto.getId());
        if (menu == null) {
            throw new BusinessException("菜单不存在");
        }
        menu.update(toProfileCommand(dto));
        menuMapper.updateById(menu);
        return menu;
    }

    private SysMenu.MenuProfileCommand toProfileCommand(MenuSaveDTO dto) {
        return SysMenu.MenuProfileCommand.builder()
            .parentId(dto.getParentId())
            .menuName(dto.getMenuName())
            .menuType(dto.getMenuType())
            .path(dto.getPath())
            .component(dto.getComponent())
            .permission(dto.getPermission())
            .icon(dto.getIcon())
            .sortOrder(dto.getSortOrder())
            .visible(dto.getVisible())
            .build();
    }

    private List<MenuVO> buildMenuTree(List<SysMenu> menus) {
        // 菜单树在代码中按 parentId 组装，避免使用多表或递归 SQL。
        Map<Long, MenuVO> voMap = menus.stream()
            .map(this::toVO)
            .collect(Collectors.toMap(MenuVO::getId, Function.identity(), (left, right) -> left));
        List<MenuVO> roots = voMap.values().stream()
            .filter(menu -> menu.getParentId() == null || Long.valueOf(SysMenu.ROOT_PARENT_ID).equals(menu.getParentId()) || !voMap.containsKey(menu.getParentId()))
            .sorted(Comparator.comparing(MenuVO::getSortOrder, Comparator.nullsLast(Integer::compareTo)))
            .toList();
        voMap.values().forEach(menu -> {
            Long parentId = menu.getParentId();
            if (parentId != null && !Long.valueOf(SysMenu.ROOT_PARENT_ID).equals(parentId) && voMap.containsKey(parentId)) {
                voMap.get(parentId).getChildren().add(menu);
            }
        });
        // 每个层级单独排序，保证后台权限树的展示顺序和 sort_order 一致。
        voMap.values().forEach(menu -> menu.getChildren().sort(Comparator.comparing(MenuVO::getSortOrder, Comparator.nullsLast(Integer::compareTo))));
        return roots;
    }

    private MenuVO toVO(SysMenu menu) {
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
