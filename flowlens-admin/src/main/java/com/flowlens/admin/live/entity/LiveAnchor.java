package com.flowlens.admin.live.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.flowlens.admin.common.BusinessException;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;
import org.springframework.util.StringUtils;

@Data
@TableName("live_anchor")
public class LiveAnchor {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String anchorName;

    private String douyinLiveId;

    private String reportToken;

    private Integer status;

    private Integer cloudCollectEnabled;

    private Integer clientOnline;

    private String clientInstanceId;

    private String clientVersion;

    private LocalDateTime clientLastHeartbeatTime;

    private Integer cloudCollecting;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public static final int STATUS_ENABLED = 1;

    public static final int STATUS_DISABLED = 0;

    public static final int FLAG_YES = 1;

    public static final int FLAG_NO = 0;

    public static LiveAnchor create(AnchorProfileCommand command, String reportToken) {
        LiveAnchor anchor = new LiveAnchor();
        LocalDateTime now = LocalDateTime.now();
        anchor.reportToken = requireText(reportToken, "客户端上报密钥不能为空");
        anchor.applyProfile(command);
        anchor.clientOnline = FLAG_NO;
        anchor.cloudCollecting = FLAG_NO;
        anchor.createTime = now;
        anchor.updateTime = now;
        return anchor;
    }

    public void updateProfile(AnchorProfileCommand command) {
        applyProfile(command);
        this.updateTime = LocalDateTime.now();
    }

    public void ensureEnabled() {
        if (!Integer.valueOf(STATUS_ENABLED).equals(status)) {
            throw new BusinessException("主播值班配置已停用");
        }
    }

    public void markClientHeartbeat(String instanceId, String version) {
        this.clientOnline = FLAG_YES;
        this.clientInstanceId = cleanNullable(instanceId);
        this.clientVersion = cleanNullable(version);
        this.clientLastHeartbeatTime = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();
    }

    public void markClientOffline() {
        this.clientOnline = FLAG_NO;
        this.updateTime = LocalDateTime.now();
    }

    public void changeCloudCollecting(boolean collecting) {
        this.cloudCollecting = collecting ? FLAG_YES : FLAG_NO;
        this.updateTime = LocalDateTime.now();
    }

    public boolean canStartCloudCollect() {
        return Integer.valueOf(FLAG_YES).equals(cloudCollectEnabled);
    }

    public boolean isClientAlive(int timeoutSeconds) {
        if (!Integer.valueOf(FLAG_YES).equals(clientOnline) || clientLastHeartbeatTime == null) {
            return false;
        }
        return Duration.between(clientLastHeartbeatTime, LocalDateTime.now()).getSeconds() <= timeoutSeconds;
    }

    private void applyProfile(AnchorProfileCommand command) {
        this.anchorName = requireText(command.getAnchorName(), "主播名称不能为空");
        this.douyinLiveId = cleanNullable(command.getDouyinLiveId());
        this.status = normalizeStatus(command.getStatus());
        this.cloudCollectEnabled = normalizeFlag(command.getCloudCollectEnabled(), FLAG_YES, "云端兜底采集开关不合法");
    }

    private static Integer normalizeStatus(Integer status) {
        if (status == null) {
            return STATUS_ENABLED;
        }
        if (!Integer.valueOf(STATUS_ENABLED).equals(status) && !Integer.valueOf(STATUS_DISABLED).equals(status)) {
            throw new BusinessException("主播配置状态不合法");
        }
        return status;
    }

    private static Integer normalizeFlag(Integer value, int defaultValue, String message) {
        if (value == null) {
            return defaultValue;
        }
        if (!Integer.valueOf(FLAG_YES).equals(value) && !Integer.valueOf(FLAG_NO).equals(value)) {
            throw new BusinessException(message);
        }
        return value;
    }

    private static String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(message);
        }
        return value.trim();
    }

    private static String cleanNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    @Data
    @Builder
    public static class AnchorProfileCommand {

        private String anchorName;

        private String douyinLiveId;

        private Integer status;

        private Integer cloudCollectEnabled;
    }
}
