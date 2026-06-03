package com.flowlens.admin.system.controller;

import com.flowlens.admin.common.ApiResponse;
import com.flowlens.admin.security.PermissionRequired;
import com.flowlens.admin.system.dto.StatusDTO;
import com.flowlens.admin.system.dto.UserSaveDTO;
import com.flowlens.admin.system.service.UserService;
import com.flowlens.admin.system.vo.UserVO;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/system/users")
public class UserController {

    private final UserService userService;

    @GetMapping
    @PermissionRequired("system:user:list")
    public ApiResponse<List<UserVO>> list(@RequestParam(name = "keyword", required = false) String keyword) {
        return ApiResponse.ok(userService.listUsers(keyword));
    }

    @PostMapping
    @PermissionRequired("system:user:save")
    public ApiResponse<UserVO> save(@Valid @RequestBody UserSaveDTO dto) {
        return ApiResponse.ok(userService.saveUser(dto));
    }

    @PutMapping("/{id}/status")
    @PermissionRequired("system:user:save")
    public ApiResponse<Void> status(@PathVariable("id") Long id, @Valid @RequestBody StatusDTO dto) {
        userService.updateStatus(id, dto.getStatus());
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    @PermissionRequired("system:user:delete")
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        userService.deleteUser(id);
        return ApiResponse.ok();
    }
}
