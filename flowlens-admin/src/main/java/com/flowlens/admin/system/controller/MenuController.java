package com.flowlens.admin.system.controller;

import com.flowlens.admin.common.ApiResponse;
import com.flowlens.admin.security.PermissionRequired;
import com.flowlens.admin.system.dto.MenuSaveDTO;
import com.flowlens.admin.system.service.MenuService;
import com.flowlens.admin.system.vo.MenuVO;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/system/menus")
public class MenuController {

    private final MenuService menuService;

    @GetMapping
    @PermissionRequired("system:menu:list")
    public ApiResponse<List<MenuVO>> list() {
        return ApiResponse.ok(menuService.listMenus());
    }

    @PostMapping
    @PermissionRequired("system:menu:save")
    public ApiResponse<MenuVO> save(@Valid @RequestBody MenuSaveDTO dto) {
        return ApiResponse.ok(menuService.saveMenu(dto));
    }

    @DeleteMapping("/{id}")
    @PermissionRequired("system:menu:delete")
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        menuService.deleteMenu(id);
        return ApiResponse.ok();
    }
}
