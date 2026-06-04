package com.flowlens.admin.live.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowlens.admin.live.entity.LiveAnchor;
import com.flowlens.admin.live.entity.LiveCollectorTask;
import com.flowlens.admin.live.entity.LiveSession;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
        liveDutyService.markStaleClientsOffline();
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
        liveDutyService.markStaleClientsOffline();
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
        if (anchor.isClientAlive(collectorProperties.getClientTimeoutSeconds())) {
            // Prefer client collector while it is alive to avoid duplicate events.
            stopCloudCollectors(session, "client online");
            liveDutyService.switchSessionSource(session.getId(), LiveSession.SOURCE_CLIENT);
            liveDutyService.markAnchorCloudCollecting(anchor.getId(), false);
            return;
        }
        if (!collectorProperties.canStartCloudCollector() || !anchor.canStartCloudCollect()) {
            stopCloudCollectors(session, "cloud disabled");
            liveDutyService.switchSessionSource(session.getId(), LiveSession.SOURCE_NONE);
            liveDutyService.markAnchorCloudCollecting(anchor.getId(), false);
            return;
        }
        if (liveDutyService.findRunningCollectorTask(session.getId()) == null) {
            startCloudCollector(session, anchor);
        }
    }

    private void probeAnchor(LiveAnchor anchor) {
        if (liveDutyService.hasRunningSession(anchor.getId())) {
            log.info("云端开播探测跳过，主播已有直播场次，anchorId={}, liveId={}, roomId={}",
                anchor.getId(), anchor.getDouyinLiveId(), anchor.getRoomId());
            return;
        }
        if (anchor.isClientAlive(collectorProperties.getClientTimeoutSeconds())) {
            log.info("云端开播探测跳过，客户端仍在线，anchorId={}, liveId={}, roomId={}",
                anchor.getId(), anchor.getDouyinLiveId(), anchor.getRoomId());
            return;
        }
        log.info("云端开播探测主播，anchorId={}, anchorName={}, liveId={}, roomId={}",
            anchor.getId(), anchor.getAnchorName(), anchor.getDouyinLiveId(), anchor.getRoomId());
        CloudProbeResult result = runCloudProbe(anchor);
        log.info("云端开播探测结果，anchorId={}, live={}, roomStatus={}, roomId={}, liveTitle={}, error={}",
            anchor.getId(), result.live(), result.roomStatus(), result.roomId(), result.liveTitle(), result.error());
        if (!result.live()) {
            return;
        }
        // Probe creates the session first; reconciliation then starts cloud collection.
        LiveSession session = liveDutyService.startCloudDetectedSession(anchor, result.roomId(), result.liveTitle());
        log.info("云端探测到主播开播，anchorId={}, sessionId={}, roomId={}", anchor.getId(), session.getId(), session.getRoomId());
        reconcileSession(session);
    }

    private void startCloudCollector(LiveSession session, LiveAnchor anchor) {
        String commandLine = buildCommandLine(session, anchor);
        if (!StringUtils.hasText(commandLine)) {
            log.warn("云端采集命令未配置，sessionId={}", session.getId());
            return;
        }
        LiveCollectorTask task = LiveCollectorTask.starting(session, commandLine);
        liveDutyService.saveCollectorTask(task);
        try {
            Process process = new ProcessBuilder(buildCommand(session, anchor))
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
            task.markRunning(process.pid());
            liveDutyService.saveCollectorTask(task);
            liveDutyService.bindCloudTask(session, task);
        } catch (IOException ex) {
            log.warn("启动云端采集失败，sessionId={}, command={}", session.getId(), commandLine, ex);
            task.markFailed(ex.getMessage());
            liveDutyService.saveCollectorTask(task);
        }
    }

    private void stopCloudCollectors(LiveSession session, String reason) {
        List<LiveCollectorTask> tasks = liveDutyService.listRunningCollectorTasks(session.getId());
        tasks.forEach(task -> {
            if (task.getProcessId() != null) {
                ProcessHandle.of(task.getProcessId()).ifPresent(process -> {
                    // Stop the room-level cloud collector when client mode takes over.
                    process.destroy();
                });
            }
            task.markStopped(reason);
            liveDutyService.saveCollectorTask(task);
        });
    }

    private CloudProbeResult runCloudProbe(LiveAnchor anchor) {
        List<String> command = buildProbeCommand(anchor);
        if (command.isEmpty()) {
            return CloudProbeResult.notLive("cloud command not configured");
        }
        Process process = null;
        CompletableFuture<String> outputFuture = null;
        try {
            process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
            Process runningProcess = process;
            outputFuture = CompletableFuture.supplyAsync(() -> readProcessOutput(runningProcess));
            boolean finished = process.waitFor(collectorProperties.getProbeTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("云端开播探测超时，anchorId={}, command={}", anchor.getId(), String.join(" ", command));
                return CloudProbeResult.notLive("probe timeout");
            }
            String output = awaitProcessOutput(outputFuture);
            if (process.exitValue() != 0) {
                log.warn("云端开播探测失败，anchorId={}, exitCode={}, output={}", anchor.getId(), process.exitValue(), trimOutput(output));
                return CloudProbeResult.notLive("probe failed");
            }
            return parseProbeOutput(output);
        } catch (IOException ex) {
            log.warn("启动云端开播探测失败，anchorId={}, command={}", anchor.getId(), String.join(" ", command), ex);
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
        return new CloudProbeResult(
            result.path("live").asBoolean(false),
            result.path("roomStatus").isMissingNode() || result.path("roomStatus").isNull() ? null : result.path("roomStatus").asInt(),
            textOrNull(result.path("roomId")),
            textOrNull(result.path("liveTitle")),
            textOrNull(result.path("error"))
        );
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

    private List<String> buildProbeCommand(LiveAnchor anchor) {
        if (!StringUtils.hasText(collectorProperties.getExecutable())) {
            return List.of();
        }
        List<String> command = new ArrayList<>();
        command.add(renderToken(collectorProperties.getExecutable(), null, anchor));
        command.addAll(buildArguments(null, anchor));
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
        return value
            .replace("{anchorId}", String.valueOf(anchor.getId()))
            .replace("{reportToken}", nullToEmpty(anchor.getReportToken()))
            .replace("{liveId}", nullToEmpty(session == null ? anchor.getDouyinLiveId() : session.getLiveId()))
            .replace("{roomId}", nullToEmpty(session == null ? anchor.getRoomId() : session.getRoomId()))
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

    private record CloudProbeResult(boolean live, Integer roomStatus, String roomId, String liveTitle, String error) {

        private static CloudProbeResult notLive(String error) {
            return new CloudProbeResult(false, null, null, null, error);
        }
    }
}
