package com.flowlens.admin.system.controller;

import com.flowlens.admin.common.ApiResponse;
import com.flowlens.admin.security.CurrentUser;
import com.flowlens.admin.system.dto.LoginDTO;
import com.flowlens.admin.system.service.AuthService;
import com.flowlens.admin.system.vo.LoginVO;
import com.flowlens.admin.system.vo.MenuVO;
import com.flowlens.admin.system.vo.UserInfoVO;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return ApiResponse.ok(authService.login(dto));
    }

    @GetMapping("/me")
    public ApiResponse<UserInfoVO> me(@AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(authService.currentUser(currentUser.userId()));
    }

    @GetMapping("/menus")
    public ApiResponse<List<MenuVO>> menus(@AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(authService.userMenus(currentUser.userId()));
    }
}
