package com.flowlens.admin.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class PermissionInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        PermissionRequired required = resolvePermission(handlerMethod);
        if (required == null || !StringUtils.hasText(required.value())) {
            return true;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUser currentUser)) {
            throw new AccessDeniedException("未登录");
        }
        // admin 角色默认拥有所有基础后台权限，方便初始化后继续维护权限树。
        if (currentUser.roles().contains("admin") || currentUser.permissions().contains(required.value())) {
            return true;
        }
        throw new AccessDeniedException("没有访问权限");
    }

    private PermissionRequired resolvePermission(HandlerMethod handlerMethod) {
        Method method = handlerMethod.getMethod();
        PermissionRequired methodPermission = method.getAnnotation(PermissionRequired.class);
        if (methodPermission != null) {
            return methodPermission;
        }
        return handlerMethod.getBeanType().getAnnotation(PermissionRequired.class);
    }
}
