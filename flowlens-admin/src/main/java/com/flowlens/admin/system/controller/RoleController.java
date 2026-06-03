package com.flowlens.admin.system.controller;

import com.flowlens.admin.common.ApiResponse;
import com.flowlens.admin.security.PermissionRequired;
import com.flowlens.admin.system.dto.RoleSaveDTO;
import com.flowlens.admin.system.service.RoleService;
import com.flowlens.admin.system.vo.RoleVO;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/system/roles")
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    @PermissionRequired("system:role:list")
    public ApiResponse<List<RoleVO>> list(@RequestParam(name = "keyword", required = false) String keyword) {
        return ApiResponse.ok(roleService.listRoles(keyword));
    }

    @PostMapping
    @PermissionRequired("system:role:save")
    public ApiResponse<RoleVO> save(@Valid @RequestBody RoleSaveDTO dto) {
        return ApiResponse.ok(roleService.saveRole(dto));
    }

    @DeleteMapping("/{id}")
    @PermissionRequired("system:role:delete")
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        roleService.deleteRole(id);
        return ApiResponse.ok();
    }
}
