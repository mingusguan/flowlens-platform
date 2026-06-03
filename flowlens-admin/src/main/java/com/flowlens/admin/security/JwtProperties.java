package com.flowlens.admin.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "flowlens.jwt")
public class JwtProperties {

    private String issuer;

    private String secret;

    private long expireMinutes;
}
