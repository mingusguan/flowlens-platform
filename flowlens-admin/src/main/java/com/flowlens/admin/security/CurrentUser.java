package com.flowlens.admin.security;

import java.util.Set;

public record CurrentUser(
    Long userId,
    String username,
    String nickname,
    Set<String> roles,
    Set<String> permissions
) {
}
