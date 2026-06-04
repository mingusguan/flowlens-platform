package com.flowlens.admin.live.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.flowlens.admin.common.BusinessException;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;
import org.springframework.util.StringUtils;

@Data
@TableName("live_session")
public class LiveSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long anchorId;

    private String liveId;

    private String roomId;

    private String liveTitle;

    private String status;

    private String activeSource;

    private Long cloudTaskId;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public static final String STATUS_LIVE = "LIVE";

    public static final String STATUS_ENDED = "ENDED";

    public static final String SOURCE_CLIENT = "CLIENT";

    public static final String SOURCE_CLOUD = "CLOUD";

    public static final String SOURCE_NONE = "NONE";

    public static LiveSession start(StartSessionCommand command) {
        if (command.getAnchorId() == null) {
            throw new BusinessException("主播 ID 不能为空");
        }
        if (!StringUtils.hasText(command.getLiveId()) && !StringUtils.hasText(command.getRoomId())) {
            throw new BusinessException("直播间 ID 和 roomId 至少填写一个");
        }
        LiveSession session = new LiveSession();
        LocalDateTime now = LocalDateTime.now();
        session.anchorId = command.getAnchorId();
        session.liveId = cleanNullable(command.getLiveId());
        session.roomId = cleanNullable(command.getRoomId());
        session.liveTitle = cleanNullable(command.getLiveTitle());
        session.status = STATUS_LIVE;
        session.activeSource = normalizeSource(command.getActiveSource());
        session.startTime = now;
        session.createTime = now;
        session.updateTime = now;
        return session;
    }

    public void ensureRunning() {
        if (!STATUS_LIVE.equals(status)) {
            throw new BusinessException("直播场次已结束");
        }
    }

    public void switchSource(String source) {
        this.activeSource = normalizeSource(source);
        this.updateTime = LocalDateTime.now();
    }

    public void bindCloudTask(Long taskId) {
        this.cloudTaskId = taskId;
        this.activeSource = SOURCE_CLOUD;
        this.updateTime = LocalDateTime.now();
    }

    public boolean releaseCloudTaskIfCurrent(Long taskId) {
        if (taskId == null || cloudTaskId == null || !cloudTaskId.equals(taskId)) {
            return false;
        }
        this.cloudTaskId = null;
        if (SOURCE_CLOUD.equals(activeSource)) {
            this.activeSource = SOURCE_NONE;
        }
        this.updateTime = LocalDateTime.now();
        return true;
    }

    public void fillProbeInfo(String liveId, String roomId, String liveTitle) {
        if (StringUtils.hasText(liveId) && !StringUtils.hasText(this.liveId)) {
            this.liveId = liveId.trim();
        }
        if (StringUtils.hasText(roomId) && !StringUtils.hasText(this.roomId)) {
            this.roomId = roomId.trim();
        }
        if (StringUtils.hasText(liveTitle) && !StringUtils.hasText(this.liveTitle)) {
            this.liveTitle = liveTitle.trim();
        }
        this.updateTime = LocalDateTime.now();
    }


    public void end() {
        if (STATUS_ENDED.equals(status)) {
            return;
        }
        this.status = STATUS_ENDED;
        this.activeSource = SOURCE_NONE;
        this.endTime = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();
    }

    public boolean isRunning() {
        return STATUS_LIVE.equals(status);
    }

    private static String normalizeSource(String source) {
        if (!StringUtils.hasText(source)) {
            return SOURCE_NONE;
        }
        String normalized = source.trim().toUpperCase();
        if (!SOURCE_CLIENT.equals(normalized) && !SOURCE_CLOUD.equals(normalized) && !SOURCE_NONE.equals(normalized)) {
            throw new BusinessException("采集源不合法");
        }
        return normalized;
    }

    private static String cleanNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    @Data
    @Builder
    public static class StartSessionCommand {

        private Long anchorId;

        private String liveId;

        private String roomId;

        private String liveTitle;

        private String activeSource;
    }
}
