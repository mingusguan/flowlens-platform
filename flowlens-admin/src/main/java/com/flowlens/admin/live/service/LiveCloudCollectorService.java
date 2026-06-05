package com.flowlens.admin.live.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowlens.admin.live.entity.LiveAnchor;
import com.flowlens.admin.live.entity.LiveCollectorTask;
import com.flowlens.admin.live.entity.LiveSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveCloudCollectorService {

    private final LiveDutyService liveDutyService;

    private final LiveCollectorProperties collectorProperties;

    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${flowlens.live.collector.schedule-delay-ms:30000}")
    public void reconcileCollectors() {
        liveDutyService.listRunningSessionEntities().forEach(this::reconcileSession);
    }

    @Scheduled(fixedDelayString = "${flowlens.live.collector.probe-delay-ms:60000}")
    public void probeLiveRooms() {
        if (!collectorProperties.canRunCloudProbe()) {
            log.info("云端开播探测跳过，cloudEnabled={}, probeEnabled={}, executable={}",
                collectorProperties.isCloudEnabled(),
                collectorProperties.isProbeEnabled(),
                collectorProperties.getExecutable());
            return;
        }
        List<LiveAnchor> anchors = liveDutyService.listCloudProbeAnchorEntities();
        log.info("云端开播探测开始，候选主播数={}，probeDelayMs={}，probeTimeoutSeconds={}",
            anchors.size(),
            collectorProperties.getProbeDelayMs(),
            collectorProperties.getProbeTimeoutSeconds());
        anchors.forEach(anchor -> {
            try {
                probeAnchor(anchor);
            } catch (Exception ex) {
                log.warn("云端开播探测异常，anchorId={}", anchor.getId(), ex);
            }
        });
    }

    private void reconcileSession(LiveSession session) {
        LiveAnchor anchor = liveDutyService.requireAnchor(session.getAnchorId());
        if (!collectorProperties.canStartCloudCollector() || !anchor.canStartCloudCollect()) {
            stopCloudCollectors(session, "cloud disabled");
            liveDutyService.switchSessionSource(session.getId(), LiveSession.SOURCE_NONE);
            liveDutyService.markAnchorCloudCollecting(anchor.getId(), false);
            return;
        }
        if (collectorProperties.canRunCloudProbe() && stopSessionIfConfirmedNotLive(session, anchor)) {
            return;
        }
        if (findAliveRunningCollectorTask(session, anchor) == null) {
            startCloudCollector(session, anchor);
        }
    }

    private void probeAnchor(LiveAnchor anchor) {
        if (liveDutyService.hasRunningSession(anchor.getId())) {
            log.info("云端开播探测跳过，主播已有直播场次，anchorId={}, liveId={}",
                anchor.getId(), anchor.getDouyinLiveId());
            return;
        }
        log.info("云端开播探测主播，anchorId={}, anchorName={}, liveId={}",
            anchor.getId(), anchor.getAnchorName(), anchor.getDouyinLiveId());
        CloudProbeResult result = runCloudProbe(anchor);
        log.info("云端开播探测结果，anchorId={}, live={}, roomStatus={}, liveId={}, roomId={}, liveTitle={}, error={}",
            anchor.getId(), result.live(), result.roomStatus(), result.liveId(), result.roomId(), result.liveTitle(), result.error());
        if (!result.live()) {
            return;
        }
        // Probe creates the session first; reconciliation then starts cloud collection.
        LiveSession session = liveDutyService.startCloudDetectedSession(anchor, result.liveId(), result.roomId(), result.liveTitle());
        log.info("云端探测到主播开播，anchorId={}, sessionId={}, roomId={}", anchor.getId(), session.getId(), session.getRoomId());
        reconcileSession(session);
    }

    private boolean stopSessionIfConfirmedNotLive(LiveSession session, LiveAnchor anchor) {
        CloudProbeResult result = runCloudProbe(session, anchor);
        log.info("云端运行场次复探结果，anchorId={}, sessionId={}, live={}, roomStatus={}, liveId={}, roomId={}, error={}",
            anchor.getId(), session.getId(), result.live(), result.roomStatus(), result.liveId(), result.roomId(), result.error());
        if (!isConfirmedNotLive(result)) {
            return false;
        }
        stopCloudCollectors(session, "confirmed not live");
        liveDutyService.endSession(session.getId());
        liveDutyService.markAnchorCloudCollecting(anchor.getId(), false);
        log.info("云端复探确认主播未开播，结束直播场次，anchorId={}, sessionId={}, liveId={}, roomId={}",
            anchor.getId(), session.getId(), session.getLiveId(), session.getRoomId());
        return true;
    }

    private void startCloudCollector(LiveSession session, LiveAnchor anchor) {
        String commandLine = buildCommandLine(session, anchor);
        if (!StringUtils.hasText(commandLine)) {
            log.warn("云端采集命令未配置，sessionId={}", session.getId());
            return;
        }
        LiveCollectorTask task = LiveCollectorTask.starting(session, commandLine);
        liveDutyService.saveCollectorTask(task);
        Process process = null;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(buildCommand(session, anchor))
                .redirectErrorStream(true);
            applyPythonUtf8Environment(processBuilder);
            process = processBuilder.start();
            task.markRunning(process.pid());
            liveDutyService.saveCollectorTask(task);
            liveDutyService.bindCloudTask(session, task);
            log.info("启动云端采集成功，sessionId={}, taskId={}, anchorId={}, processId={}, liveId={}, roomId={}",
                session.getId(), task.getId(), anchor.getId(), task.getProcessId(), task.getLiveId(), task.getRoomId());
            monitorCloudCollectorOutput(process, task);
        } catch (IOException ex) {
            log.warn("启动云端采集失败，sessionId={}, command={}", session.getId(), commandLine, ex);
            task.markFailed(ex.getMessage());
            liveDutyService.saveCollectorTask(task);
            liveDutyService.endSession(session.getId());
            liveDutyService.markAnchorCloudCollecting(anchor.getId(), false);
        } catch (RuntimeException ex) {
            if (process != null && process.isAlive()) {
                process.destroy();
            }
            log.warn("绑定云端采集任务失败，sessionId={}, taskId={}, command={}", session.getId(), task.getId(), commandLine, ex);
            task.markFailed(ex.getMessage());
            liveDutyService.saveCollectorTask(task);
            if (liveDutyService.endSessionIfCurrentCloudTask(session.getId(), task.getId())) {
                liveDutyService.markAnchorCloudCollecting(anchor.getId(), false);
            } else if (liveDutyService.releaseCloudTaskIfCurrent(session.getId(), task.getId())) {
                liveDutyService.markAnchorCloudCollecting(anchor.getId(), false);
            }
        }
    }

    private LiveCollectorTask findAliveRunningCollectorTask(LiveSession session, LiveAnchor anchor) {
        List<LiveCollectorTask> tasks = liveDutyService.listRunningCollectorTasks(session.getId());
        if (tasks.isEmpty()) {
            return null;
        }
        LiveCollectorTask aliveTask = null;
        for (LiveCollectorTask task : tasks) {
            if (task.getProcessId() == null) {
                markCollectorTaskNotAlive(session, anchor, task, "process id missing");
                continue;
            }
            boolean alive = ProcessHandle.of(task.getProcessId())
                .map(ProcessHandle::isAlive)
                .orElse(false);
            if (!alive) {
                markCollectorTaskNotAlive(session, anchor, task, "process not alive");
                continue;
            }
            if (aliveTask == null) {
                aliveTask = task;
                continue;
            }
            ProcessHandle.of(task.getProcessId()).ifPresent(ProcessHandle::destroy);
            task.markStopped("duplicate collector");
            liveDutyService.saveCollectorTask(task);
            liveDutyService.releaseCloudTaskIfCurrent(session.getId(), task.getId());
            log.warn("停止重复云端采集进程，sessionId={}, taskId={}, processId={}",
                session.getId(), task.getId(), task.getProcessId());
        }
        if (aliveTask == null) {
            return null;
        }
        if (!Objects.equals(session.getCloudTaskId(), aliveTask.getId())) {
            liveDutyService.bindCloudTask(session, aliveTask);
            log.info("补齐云端采集任务绑定，sessionId={}, taskId={}, anchorId={}, processId={}",
                session.getId(), aliveTask.getId(), anchor.getId(), aliveTask.getProcessId());
        }
        return aliveTask;
    }

    private void markCollectorTaskNotAlive(LiveSession session, LiveAnchor anchor, LiveCollectorTask task, String reason) {
        task.markFailed(reason);
        liveDutyService.saveCollectorTask(task);
        if (liveDutyService.releaseCloudTaskIfCurrent(session.getId(), task.getId())) {
            liveDutyService.markAnchorCloudCollecting(anchor.getId(), false);
        }
        log.warn("云端采集进程已退出，准备重新启动，sessionId={}, taskId={}, processId={}",
            session.getId(), task.getId(), task.getProcessId());
    }

    private void monitorCloudCollectorOutput(Process process, LiveCollectorTask task) {
        CompletableFuture.runAsync(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (StringUtils.hasText(line)) {
                        log.info("云端采集输出，taskId={}, sessionId={}, anchorId={}, output={}",
                            task.getId(), task.getSessionId(), task.getAnchorId(), trimOutput(line));
                    }
                }
                int exitCode = process.waitFor();
                if (!liveDutyService.finishCollectorTaskIfActive(task, exitCode)) {
                    log.info("云端采集任务已由业务流程收口，taskId={}, sessionId={}, exitCode={}",
                        task.getId(), task.getSessionId(), exitCode);
                    return;
                }
                if (liveDutyService.endSessionIfCurrentCloudTask(task.getSessionId(), task.getId())) {
                    liveDutyService.markAnchorCloudCollecting(task.getAnchorId(), false);
                    log.info("云端采集进程退出，自动结束直播场次，taskId={}, sessionId={}, anchorId={}",
                        task.getId(), task.getSessionId(), task.getAnchorId());
                } else if (liveDutyService.releaseCloudTaskIfCurrent(task.getSessionId(), task.getId())) {
                    liveDutyService.markAnchorCloudCollecting(task.getAnchorId(), false);
                }
                log.warn("云端采集进程退出，taskId={}, sessionId={}, anchorId={}, processId={}, exitCode={}",
                    task.getId(), task.getSessionId(), task.getAnchorId(), task.getProcessId(), exitCode);
            } catch (IOException ex) {
                log.warn("读取云端采集输出失败，taskId={}, sessionId={}", task.getId(), task.getSessionId(), ex);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private void stopCloudCollectors(LiveSession session, String reason) {
        List<LiveCollectorTask> tasks = liveDutyService.listRunningCollectorTasks(session.getId());
        tasks.forEach(task -> {
            if (task.getProcessId() != null) {
                ProcessHandle.of(task.getProcessId()).ifPresent(process -> {
                    // 云端复探确认无需继续采集时，停止对应直播间的采集进程。
                    process.destroy();
                });
            }
            task.markStopped(reason);
            liveDutyService.saveCollectorTask(task);
            liveDutyService.releaseCloudTaskIfCurrent(session.getId(), task.getId());
        });
    }

    private CloudProbeResult runCloudProbe(LiveAnchor anchor) {
        return runCloudProbe(null, anchor);
    }

    private CloudProbeResult runCloudProbe(LiveSession session, LiveAnchor anchor) {
        List<String> command = buildProbeCommand(session, anchor);
        if (command.isEmpty()) {
            return CloudProbeResult.notLive("cloud command not configured");
        }
        Process process = null;
        CompletableFuture<String> outputFuture = null;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command)
                .redirectErrorStream(true);
            applyPythonUtf8Environment(processBuilder);
            process = processBuilder.start();
            Process runningProcess = process;
            outputFuture = CompletableFuture.supplyAsync(() -> readProcessOutput(runningProcess));
            boolean finished = process.waitFor(collectorProperties.getProbeTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("云端开播探测超时，anchorId={}, sessionId={}, command={}", anchor.getId(), session == null ? null : session.getId(), String.join(" ", command));
                return CloudProbeResult.notLive("probe timeout");
            }
            String output = awaitProcessOutput(outputFuture);
            if (process.exitValue() != 0) {
                log.warn("云端开播探测失败，anchorId={}, sessionId={}, exitCode={}, output={}", anchor.getId(), session == null ? null : session.getId(), process.exitValue(), trimOutput(output));
                return CloudProbeResult.notLive("probe failed");
            }
            return parseProbeOutput(output);
        } catch (IOException ex) {
            log.warn("启动云端开播探测失败，anchorId={}, sessionId={}, command={}", anchor.getId(), session == null ? null : session.getId(), String.join(" ", command), ex);
            return CloudProbeResult.notLive(ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return CloudProbeResult.notLive("probe interrupted");
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            if (outputFuture != null) {
                outputFuture.cancel(true);
            }
        }
    }

    private CloudProbeResult parseProbeOutput(String output) {
        if (!StringUtils.hasText(output)) {
            return CloudProbeResult.notLive("empty probe output");
        }
        JsonNode result = null;
        for (String line : output.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                try {
                    result = objectMapper.readTree(trimmed);
                } catch (IOException ignored) {
                    result = null;
                }
            }
        }
        if (result == null) {
            return CloudProbeResult.notLive("probe json not found");
        }
        Integer roomStatus = result.path("roomStatus").isMissingNode() || result.path("roomStatus").isNull() ? null : result.path("roomStatus").asInt();
        boolean live = result.path("live").asBoolean(false);
        if (roomStatus != null && roomStatus != 0) {
            // 抖音开播状态以 roomStatus=0 为准，避免旧脚本把非直播状态的流地址误判成开播。
            live = false;
        }
        return new CloudProbeResult(
            live,
            roomStatus,
            textOrNull(result.path("liveId")),
            textOrNull(result.path("roomId")),
            textOrNull(result.path("liveTitle")),
            textOrNull(result.path("error"))
        );
    }

    private void applyPythonUtf8Environment(ProcessBuilder processBuilder) {
        // Windows 默认控制台编码可能是 GBK，直播标题包含特殊字符时会导致 Python print 失败。
        processBuilder.environment().put("PYTHONIOENCODING", "utf-8");
        processBuilder.environment().put("PYTHONUTF8", "1");
    }

    private String readProcessOutput(Process process) {
        try (InputStream input = process.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return "";
        }
    }

    private String awaitProcessOutput(CompletableFuture<String> outputFuture) {
        try {
            return outputFuture.get(2, TimeUnit.SECONDS);
        } catch (Exception ex) {
            return "";
        }
    }

    private String buildCommandLine(LiveSession session, LiveAnchor anchor) {
        if (!StringUtils.hasText(collectorProperties.getExecutable())) {
            return null;
        }
        return renderToken(collectorProperties.getExecutable(), session, anchor) + " "
            + String.join(" ", buildArguments(session, anchor));
    }

    private List<String> buildCommand(LiveSession session, LiveAnchor anchor) {
        List<String> command = new ArrayList<>();
        command.add(renderToken(collectorProperties.getExecutable(), session, anchor));
        command.addAll(buildArguments(session, anchor));
        return command;
    }

    private List<String> buildProbeCommand(LiveSession session, LiveAnchor anchor) {
        if (!StringUtils.hasText(collectorProperties.getExecutable())) {
            return List.of();
        }
        List<String> command = new ArrayList<>();
        command.add(renderToken(collectorProperties.getExecutable(), session, anchor));
        command.addAll(buildArguments(session, anchor));
        command.add("--probe");
        return command;
    }

    private List<String> buildArguments(LiveSession session, LiveAnchor anchor) {
        List<String> arguments = new ArrayList<>();
        for (String arg : collectorProperties.getArgs()) {
            String rendered = renderToken(arg, session, anchor);
            if (StringUtils.hasText(rendered)) {
                arguments.add(rendered);
                continue;
            }
            // Drop the previous option too, so an empty value cannot leave a dangling --room-id.
            if (!arguments.isEmpty() && arguments.get(arguments.size() - 1).startsWith("--")) {
                arguments.remove(arguments.size() - 1);
            }
        }
        return arguments;
    }

    private String renderToken(String value, LiveSession session, LiveAnchor anchor) {
        String liveId = session == null ? anchor.getDouyinLiveId() : firstText(session.getLiveId(), anchor.getDouyinLiveId());
        String roomId = session == null ? null : session.getRoomId();
        return value
            .replace("{anchorId}", String.valueOf(anchor.getId()))
            .replace("{reportToken}", nullToEmpty(anchor.getReportToken()))
            .replace("{liveId}", nullToEmpty(liveId))
            .replace("{roomId}", nullToEmpty(roomId))
            .replace("{sessionId}", session == null ? "" : String.valueOf(session.getId()));
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String trimOutput(String output) {
        if (!StringUtils.hasText(output)) {
            return "";
        }
        String trimmed = output.trim();
        return trimmed.length() > 1000 ? trimmed.substring(0, 1000) : trimmed;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean isConfirmedNotLive(CloudProbeResult result) {
        return result != null && !result.live() && !StringUtils.hasText(result.error());
    }

    private record CloudProbeResult(boolean live, Integer roomStatus, String liveId, String roomId, String liveTitle, String error) {

        private static CloudProbeResult notLive(String error) {
            return new CloudProbeResult(false, null, null, null, null, error);
        }
    }
}
