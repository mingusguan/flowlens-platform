package com.flowlens.admin.live.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import org.springframework.util.StringUtils;

@Data
@TableName("live_collector_task")
public class LiveCollectorTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private Long anchorId;

    private String liveId;

    private String roomId;

    private String status;

    private Long processId;

    private String commandLine;

    private String lastError;

    private LocalDateTime startTime;

    private LocalDateTime stopTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public static final String STATUS_STARTING = "STARTING";

    public static final String STATUS_RUNNING = "RUNNING";

    public static final String STATUS_STOPPED = "STOPPED";

    public static final String STATUS_FAILED = "FAILED";

    public static LiveCollectorTask starting(LiveSession session, String commandLine) {
        LiveCollectorTask task = new LiveCollectorTask();
        LocalDateTime now = LocalDateTime.now();
        task.sessionId = session.getId();
        task.anchorId = session.getAnchorId();
        task.liveId = session.getLiveId();
        task.roomId = session.getRoomId();
        task.status = STATUS_STARTING;
        task.commandLine = commandLine;
        task.startTime = now;
        task.createTime = now;
        task.updateTime = now;
        return task;
    }

    public void markRunning(Long processId) {
        this.processId = processId;
        this.status = STATUS_RUNNING;
        this.lastError = null;
        this.updateTime = LocalDateTime.now();
    }

    public void markFailed(String message) {
        this.status = STATUS_FAILED;
        this.lastError = trimMessage(message);
        this.stopTime = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();
    }

    public void markStopped(String message) {
        this.status = STATUS_STOPPED;
        this.lastError = trimMessage(message);
        this.stopTime = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();
    }

    private String trimMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return null;
        }
        String trimmed = message.trim();
        return trimmed.length() > 1000 ? trimmed.substring(0, 1000) : trimmed;
    }
}
