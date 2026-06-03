package com.flowlens.admin.live.service;

import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Data
@Component
@ConfigurationProperties(prefix = "flowlens.live.collector")
public class LiveCollectorProperties {

    private int clientTimeoutSeconds = 45;

    private long scheduleDelayMs = 30000L;

    private boolean probeEnabled = true;

    private long probeDelayMs = 60000L;

    private int probeTimeoutSeconds = 15;

    private boolean cloudEnabled = false;

    private String executable;

    private List<String> args = List.of();

    public boolean canStartCloudCollector() {
        return cloudEnabled && StringUtils.hasText(executable);
    }

    public boolean canRunCloudProbe() {
        return canStartCloudCollector() && probeEnabled;
    }
}
