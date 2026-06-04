package com.flowlens.admin.live.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.flowlens.admin.common.BusinessException;
import com.flowlens.admin.live.dto.LiveAnchorSaveDTO;
import com.flowlens.admin.live.dto.LiveClientHeartbeatDTO;
import com.flowlens.admin.live.dto.LiveEventReportDTO;
import com.flowlens.admin.live.dto.LiveSessionStartDTO;
import com.flowlens.admin.live.entity.LiveAnchor;
import com.flowlens.admin.live.entity.LiveCollectorTask;
import com.flowlens.admin.live.entity.LiveEvent;
import com.flowlens.admin.live.entity.LiveEventRaw;
import com.flowlens.admin.live.entity.LiveSession;
import com.flowlens.admin.live.entity.LiveSessionStat;
import com.flowlens.admin.live.entity.LiveUserGiftStat;
import com.flowlens.admin.live.mapper.LiveAnchorMapper;
import com.flowlens.admin.live.mapper.LiveCollectorTaskMapper;
import com.flowlens.admin.live.mapper.LiveEventMapper;
import com.flowlens.admin.live.mapper.LiveEventRawMapper;
import com.flowlens.admin.live.mapper.LiveSessionMapper;
import com.flowlens.admin.live.mapper.LiveSessionStatMapper;
import com.flowlens.admin.live.mapper.LiveUserGiftStatMapper;
import com.flowlens.admin.live.sharding.LiveEventShardTableService;
import com.flowlens.admin.live.sharding.LiveEventTableRouter;
import com.flowlens.admin.live.service.DouyinLiveInputResolver.ResolvedDouyinLiveInput;
import com.flowlens.admin.live.vo.LiveAnchorVO;
import com.flowlens.admin.live.vo.LiveCollectorTaskVO;
import com.flowlens.admin.live.vo.LiveGiftRankVO;
import com.flowlens.admin.live.vo.LiveSessionVO;
import com.flowlens.admin.live.vo.LiveSummaryVO;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveDutyService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final LiveAnchorMapper anchorMapper;

    private final LiveSessionMapper sessionMapper;

    private final LiveEventMapper eventMapper;

    private final LiveEventRawMapper eventRawMapper;

    private final LiveSessionStatMapper sessionStatMapper;

    private final LiveUserGiftStatMapper userGiftStatMapper;

    private final LiveCollectorTaskMapper collectorTaskMapper;

    private final LiveCollectorProperties collectorProperties;

    private final LiveEventShardTableService shardTableService;

    private final DouyinLiveInputResolver douyinLiveInputResolver;

    public List<LiveAnchorVO> listAnchors(String keyword) {
        LambdaQueryWrapper<LiveAnchor> wrapper = new LambdaQueryWrapper<LiveAnchor>()
            .orderByDesc(LiveAnchor::getCreateTime);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(item -> item.like(LiveAnchor::getAnchorName, keyword)
                .or()
                .like(LiveAnchor::getDouyinLiveId, keyword));
        }
        return anchorMapper.selectList(wrapper).stream()
            .map(this::toAnchorVO)
            .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public LiveAnchorVO saveAnchor(LiveAnchorSaveDTO dto) {
        LiveAnchor anchor = dto.getId() == null ? createAnchor(dto) : updateAnchor(dto);
        return toAnchorVO(anchor);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteAnchor(Long id) {
        Long runningCount = sessionMapper.selectCount(new LambdaQueryWrapper<LiveSession>()
            .eq(LiveSession::getAnchorId, id)
            .eq(LiveSession::getStatus, LiveSession.STATUS_LIVE));
        if (runningCount > 0) {
            throw new BusinessException("主播还有直播场次在采集中，不能删除");
        }
        anchorMapper.deleteById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public LiveSessionVO startSession(Long anchorId, LiveSessionStartDTO dto) {
        LiveAnchor anchor = requireAnchor(anchorId);
        anchor.ensureEnabled();
        LiveSession exists = findRunningSession(anchorId);
        if (exists != null) {
            return toSessionVO(exists, Map.of(anchor.getId(), anchor));
        }
        ResolvedDouyinLiveInput resolvedInput = douyinLiveInputResolver.resolve(dto.getLiveId());
        String inputLiveId = firstText(resolvedInput.liveId(), resolvedInput.canonicalInput());
        if (!StringUtils.hasText(inputLiveId) && !StringUtils.hasText(resolvedInput.roomId())) {
            inputLiveId = firstText(dto.getLiveId());
        }
        String liveId = firstText(inputLiveId, anchor.getDouyinLiveId());
        String roomId = firstText(dto.getRoomId(), resolvedInput.roomId());
        String liveTitle = firstText(dto.getLiveTitle(), resolvedInput.liveTitle());
        String source = anchor.isClientAlive(collectorProperties.getClientTimeoutSeconds())
            ? LiveSession.SOURCE_CLIENT
            : LiveSession.SOURCE_NONE;
        LiveSession session = LiveSession.start(LiveSession.StartSessionCommand.builder()
            .anchorId(anchorId)
            .liveId(liveId)
            .roomId(roomId)
            .liveTitle(liveTitle)
            .activeSource(source)
            .build());
        sessionMapper.insert(session);
        return toSessionVO(session, Map.of(anchor.getId(), anchor));
    }

    @Transactional(rollbackFor = Exception.class)
    public void endSession(Long sessionId) {
        LiveSession session = requireSession(sessionId);
        session.end();
        sessionMapper.updateById(session);
    }

    public List<LiveSessionVO> listSessions(Long anchorId, String status) {
        LambdaQueryWrapper<LiveSession> wrapper = new LambdaQueryWrapper<LiveSession>()
            .orderByDesc(LiveSession::getStartTime);
        if (anchorId != null) {
            wrapper.eq(LiveSession::getAnchorId, anchorId);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(LiveSession::getStatus, status.trim().toUpperCase());
        }
        List<LiveSession> sessions = sessionMapper.selectList(wrapper);
        if (sessions.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, LiveAnchor> anchors = selectAnchorsByIds(sessions.stream()
            .map(LiveSession::getAnchorId)
            .filter(Objects::nonNull)
            .distinct()
            .toList());
        return sessions.stream()
            .map(session -> toSessionVO(session, anchors))
            .toList();
    }

    public LiveSummaryVO getSummary(Long sessionId) {
        LiveSession session = requireSession(sessionId);
        LiveAnchor anchor = requireAnchor(session.getAnchorId());
        LiveSessionStat stat = sessionStatMapper.selectOne(new LambdaQueryWrapper<LiveSessionStat>()
            .eq(LiveSessionStat::getSessionId, sessionId)
            .last("limit 1"));
        return LiveSummaryVO.builder()
            .sessionId(session.getId())
            .anchorId(session.getAnchorId())
            .anchorName(anchor.getAnchorName())
            .liveId(session.getLiveId())
            .roomId(session.getRoomId())
            .status(session.getStatus())
            .startTime(session.getStartTime())
            .endTime(session.getEndTime())
            .commentCount(stat == null ? 0L : stat.getCommentCount())
            .likeCount(stat == null ? 0L : stat.getLikeCount())
            .giftCount(stat == null ? 0 : safeLongToInt(stat.getGiftCount()))
            .giftValue(stat == null ? 0L : stat.getGiftValue())
            .viewerCount(stat == null ? 0L : stat.getViewerCount())
            .giftRank(buildGiftRank(sessionId))
            .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public LiveSessionVO clientHeartbeat(LiveClientHeartbeatDTO dto) {
        LiveAnchor anchor = requireAnchorForReport(dto.getAnchorId(), dto.getReportToken());
        anchor.ensureEnabled();
        anchor.markClientHeartbeat(dto.getClientInstanceId(), dto.getClientVersion());
        anchorMapper.updateById(anchor);
        if ("ENDED".equalsIgnoreCase(dto.getLiveStatus())) {
            LiveSession running = findRunningSession(anchor.getId());
            if (running != null) {
                running.end();
                sessionMapper.updateById(running);
                return toSessionVO(running, Map.of(anchor.getId(), anchor));
            }
            return null;
        }
        if ("LIVE".equalsIgnoreCase(dto.getLiveStatus()) || "RUNNING".equalsIgnoreCase(dto.getLiveStatus())) {
            LiveSession session = ensureRunningSession(anchor, dto.getLiveId(), dto.getRoomId(), dto.getLiveTitle(), LiveSession.SOURCE_CLIENT);
            session.switchSource(LiveSession.SOURCE_CLIENT);
            sessionMapper.updateById(session);
            return toSessionVO(session, Map.of(anchor.getId(), anchor));
        }
        return null;
    }

    @Transactional(rollbackFor = Exception.class)
    public void reportClientEvent(LiveEventReportDTO dto) {
        acceptEvent(dto, LiveEvent.SOURCE_CLIENT);
    }

    @Transactional(rollbackFor = Exception.class)
    public void reportCloudEvent(LiveEventReportDTO dto) {
        acceptEvent(dto, LiveEvent.SOURCE_CLOUD);
    }

    public List<LiveSession> listRunningSessionEntities() {
        return sessionMapper.selectList(new LambdaQueryWrapper<LiveSession>()
            .eq(LiveSession::getStatus, LiveSession.STATUS_LIVE));
    }

    public List<LiveAnchor> listCloudProbeAnchorEntities() {
        return anchorMapper.selectList(new LambdaQueryWrapper<LiveAnchor>()
                .eq(LiveAnchor::getStatus, LiveAnchor.STATUS_ENABLED)
                .eq(LiveAnchor::getCloudCollectEnabled, LiveAnchor.FLAG_YES)
                .orderByDesc(LiveAnchor::getCreateTime))
            .stream()
            .filter(anchor -> StringUtils.hasText(anchor.getDouyinLiveId()))
            .toList();
    }

    public boolean hasRunningSession(Long anchorId) {
        return findRunningSession(anchorId) != null;
    }

    @Transactional(rollbackFor = Exception.class)
    public LiveSession startCloudDetectedSession(LiveAnchor anchor, String liveId, String roomId, String liveTitle) {
        anchor.ensureEnabled();
        LiveSession running = findRunningSession(anchor.getId());
        if (running != null) {
            running.fillProbeInfo(liveId, roomId, liveTitle);
            sessionMapper.updateById(running);
            return running;
        }
        // Probe only confirms live status; the source switches to CLOUD after collector startup.
        LiveSession session = LiveSession.start(LiveSession.StartSessionCommand.builder()
            .anchorId(anchor.getId())
            .liveId(firstText(liveId, anchor.getDouyinLiveId()))
            .roomId(roomId)
            .liveTitle(liveTitle)
            .activeSource(LiveSession.SOURCE_NONE)
            .build());
        sessionMapper.insert(session);
        return session;
    }

    public LiveAnchor requireAnchor(Long anchorId) {
        LiveAnchor anchor = anchorMapper.selectById(anchorId);
        if (anchor == null) {
            throw new BusinessException("主播值班配置不存在");
        }
        return anchor;
    }

    @Transactional(rollbackFor = Exception.class)
    public void markStaleClientsOffline() {
        List<LiveAnchor> anchors = anchorMapper.selectList(new LambdaQueryWrapper<LiveAnchor>()
            .eq(LiveAnchor::getClientOnline, LiveAnchor.FLAG_YES));
        anchors.stream()
            .filter(anchor -> !anchor.isClientAlive(collectorProperties.getClientTimeoutSeconds()))
            .forEach(anchor -> {
                // 心跳超时后先标记客户端离线，云端调度器下一步再决定是否接管。
                anchor.markClientOffline();
                anchorMapper.updateById(anchor);
            });
    }

    @Transactional(rollbackFor = Exception.class)
    public void bindCloudTask(LiveSession session, LiveCollectorTask task) {
        session.bindCloudTask(task.getId());
        sessionMapper.updateById(session);
        LiveAnchor anchor = requireAnchor(session.getAnchorId());
        anchor.changeCloudCollecting(true);
        anchorMapper.updateById(anchor);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean releaseCloudTaskIfCurrent(Long sessionId, Long taskId) {
        LiveSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            return false;
        }
        if (!session.releaseCloudTaskIfCurrent(taskId)) {
            return session.getCloudTaskId() == null;
        }
        sessionMapper.updateById(session);
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public void switchSessionSource(Long sessionId, String source) {
        LiveSession session = requireSession(sessionId);
        session.switchSource(source);
        sessionMapper.updateById(session);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markAnchorCloudCollecting(Long anchorId, boolean collecting) {
        LiveAnchor anchor = requireAnchor(anchorId);
        anchor.changeCloudCollecting(collecting);
        anchorMapper.updateById(anchor);
    }

    public LiveCollectorTask findRunningCollectorTask(Long sessionId) {
        return collectorTaskMapper.selectOne(new LambdaQueryWrapper<LiveCollectorTask>()
            .eq(LiveCollectorTask::getSessionId, sessionId)
            .eq(LiveCollectorTask::getStatus, LiveCollectorTask.STATUS_RUNNING)
            .last("limit 1"));
    }

    public List<LiveCollectorTask> listRunningCollectorTasks(Long sessionId) {
        return collectorTaskMapper.selectList(new LambdaQueryWrapper<LiveCollectorTask>()
            .eq(LiveCollectorTask::getSessionId, sessionId)
            .eq(LiveCollectorTask::getStatus, LiveCollectorTask.STATUS_RUNNING));
    }

    @Transactional(rollbackFor = Exception.class)
    public LiveCollectorTask saveCollectorTask(LiveCollectorTask task) {
        if (task.getId() == null) {
            collectorTaskMapper.insert(task);
        } else {
            collectorTaskMapper.updateById(task);
        }
        return task;
    }

    public List<LiveCollectorTaskVO> listCollectorTasks(Long sessionId) {
        return collectorTaskMapper.selectList(new LambdaQueryWrapper<LiveCollectorTask>()
                .eq(LiveCollectorTask::getSessionId, sessionId)
                .orderByDesc(LiveCollectorTask::getCreateTime))
            .stream()
            .map(this::toCollectorTaskVO)
            .toList();
    }

    private LiveAnchor createAnchor(LiveAnchorSaveDTO dto) {
        LiveAnchor anchor = LiveAnchor.create(toAnchorCommand(dto), generateReportToken());
        anchorMapper.insert(anchor);
        return anchor;
    }

    private LiveAnchor updateAnchor(LiveAnchorSaveDTO dto) {
        LiveAnchor anchor = requireAnchor(dto.getId());
        anchor.updateProfile(toAnchorCommand(dto));
        anchorMapper.updateById(anchor);
        return anchor;
    }

    private void acceptEvent(LiveEventReportDTO dto, String source) {
        LiveAnchor anchor = requireAnchorForReport(dto.getAnchorId(), dto.getReportToken());
        anchor.ensureEnabled();
        LiveSession session = ensureRunningSession(anchor, dto.getLiveId(), dto.getRoomId(), dto.getLiveTitle(), source);
        LocalDateTime defaultEventTime = LocalDateTime.now();
        LiveEvent event = LiveEvent.fromReport(session, dto, source, defaultEventTime);
        if (!shouldPersistEventDetail(event)) {
            updateEventStats(event);
            logReceivedEvent(event);
            return;
        }
        String monthSuffix = LiveEventTableRouter.resolveMonthSuffix(event.getEventTime());
        shardTableService.ensureMonthTables(monthSuffix);
        LiveEventTableRouter.useMonth(monthSuffix);
        try {
            if (hasDuplicateEvent(event)) {
                return;
            }
            try {
                eventMapper.insert(event);
            } catch (DuplicateKeyException ignored) {
                return;
            }
            saveRawPayload(event, dto.getRawPayload());
        } finally {
            LiveEventTableRouter.clear();
        }
        updateEventStats(event);
        logReceivedEvent(event);
        if (event.isEndEvent()) {
            session.end();
            sessionMapper.updateById(session);
        }
    }

    private boolean shouldPersistEventDetail(LiveEvent event) {
        // 观看人数快照和进房事件频率高，只参与场次统计，不写入月度事件明细表。
        return !LiveEvent.TYPE_ROOM_STATS.equals(event.getEventType())
            && !LiveEvent.TYPE_MEMBER.equals(event.getEventType());
    }

    private void saveRawPayload(LiveEvent event, String rawPayload) {
        if (!StringUtils.hasText(rawPayload)) {
            return;
        }
        eventRawMapper.insert(LiveEventRaw.fromEvent(event, rawPayload.trim()));
    }

    private void updateEventStats(LiveEvent event) {
        updateSessionStat(event);
        if (LiveEvent.TYPE_GIFT.equals(event.getEventType())) {
            updateUserGiftStat(event);
        }
    }

    private void updateSessionStat(LiveEvent event) {
        LambdaUpdateWrapper<LiveSessionStat> wrapper = new LambdaUpdateWrapper<LiveSessionStat>()
            .eq(LiveSessionStat::getSessionId, event.getSessionId())
            .set(LiveSessionStat::getAnchorId, event.getAnchorId())
            .set(LiveSessionStat::getUpdateTime, LocalDateTime.now());
        if (LiveEvent.TYPE_COMMENT.equals(event.getEventType())) {
            wrapper.setSql("comment_count = comment_count + 1");
        } else if (LiveEvent.TYPE_LIKE.equals(event.getEventType())) {
            wrapper.setSql("like_count = like_count + " + positiveSqlValue(event.getLikeCount()));
        } else if (LiveEvent.TYPE_GIFT.equals(event.getEventType())) {
            wrapper.setSql("gift_count = gift_count + " + positiveSqlValue(event.getGiftCount()))
                .setSql("gift_value = gift_value + " + positiveSqlValue(event.getGiftValue()));
        } else if (LiveEvent.TYPE_ROOM_STATS.equals(event.getEventType())) {
            wrapper.setSql("viewer_count = greatest(viewer_count, " + positiveSqlValue(event.getViewerCount()) + ")");
        } else {
            return;
        }
        if (sessionStatMapper.update(null, wrapper) > 0) {
            return;
        }
        try {
            sessionStatMapper.insert(LiveSessionStat.fromEvent(event));
        } catch (DuplicateKeyException ignored) {
            sessionStatMapper.update(null, wrapper);
        }
    }

    private void updateUserGiftStat(LiveEvent event) {
        String userKey = buildGiftUserKey(event);
        LambdaUpdateWrapper<LiveUserGiftStat> wrapper = new LambdaUpdateWrapper<LiveUserGiftStat>()
            .eq(LiveUserGiftStat::getSessionId, event.getSessionId())
            .eq(LiveUserGiftStat::getUserKey, userKey)
            .set(LiveUserGiftStat::getAnchorId, event.getAnchorId())
            .set(StringUtils.hasText(event.getUserId()), LiveUserGiftStat::getUserId, event.getUserId())
            .set(StringUtils.hasText(event.getDouyinAccount()), LiveUserGiftStat::getDouyinAccount, event.getDouyinAccount())
            .set(StringUtils.hasText(event.getNickname()), LiveUserGiftStat::getNickname, event.getNickname())
            .set(LiveUserGiftStat::getUpdateTime, LocalDateTime.now())
            .setSql("gift_count = gift_count + " + positiveSqlValue(event.getGiftCount()))
            .setSql("gift_value = gift_value + " + positiveSqlValue(event.getGiftValue()))
            .setSql("gift_event_count = gift_event_count + 1");
        if (userGiftStatMapper.update(null, wrapper) > 0) {
            return;
        }
        try {
            userGiftStatMapper.insert(LiveUserGiftStat.fromGiftEvent(event, userKey));
        } catch (DuplicateKeyException ignored) {
            userGiftStatMapper.update(null, wrapper);
        }
    }

    private void logReceivedEvent(LiveEvent event) {
        log.info("收到直播事件，source={}, sessionId={}, anchorId={}, roomId={}, liveId={}, type={}, msgId={}, userId={}, douyinAccount={}, nickname={}, content={}, giftId={}, giftName={}, giftCount={}, giftValue={}, likeCount={}, viewerCount={}, eventTime={}",
            event.getSource(),
            event.getSessionId(),
            event.getAnchorId(),
            event.getRoomId(),
            event.getLiveId(),
            event.getEventType(),
            event.getMsgId(),
            event.getUserId(),
            event.getDouyinAccount(),
            event.getNickname(),
            abbreviate(event.getContent(), 120),
            event.getGiftId(),
            event.getGiftName(),
            event.getGiftCount(),
            event.getGiftValue(),
            event.getLikeCount(),
            event.getViewerCount(),
            event.getEventTime());
    }

    private String abbreviate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private LiveSession ensureRunningSession(LiveAnchor anchor, String liveId, String roomId, String liveTitle, String source) {
        LiveSession running = findRunningSession(anchor.getId());
        if (running != null) {
            return running;
        }
        String resolvedLiveId = firstText(liveId, anchor.getDouyinLiveId());
        LiveSession session = LiveSession.start(LiveSession.StartSessionCommand.builder()
            .anchorId(anchor.getId())
            .liveId(resolvedLiveId)
            .roomId(roomId)
            .liveTitle(liveTitle)
            .activeSource(source)
            .build());
        sessionMapper.insert(session);
        return session;
    }

    private LiveSession findRunningSession(Long anchorId) {
        return sessionMapper.selectOne(new LambdaQueryWrapper<LiveSession>()
            .eq(LiveSession::getAnchorId, anchorId)
            .eq(LiveSession::getStatus, LiveSession.STATUS_LIVE)
            .last("limit 1"));
    }

    private LiveSession requireSession(Long sessionId) {
        LiveSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException("直播场次不存在");
        }
        return session;
    }

    private LiveAnchor requireAnchorForReport(Long anchorId, String reportToken) {
        LiveAnchor anchor = requireAnchor(anchorId);
        if (!StringUtils.hasText(reportToken) || !reportToken.equals(anchor.getReportToken())) {
            throw new BusinessException(401, "客户端上报密钥不正确");
        }
        return anchor;
    }

    private boolean hasDuplicateEvent(LiveEvent event) {
        if (!StringUtils.hasText(event.getMsgId()) || !StringUtils.hasText(event.getRoomId())) {
            return false;
        }
        Long count = eventMapper.selectCount(new LambdaQueryWrapper<LiveEvent>()
            .eq(LiveEvent::getRoomId, event.getRoomId())
            .eq(LiveEvent::getEventType, event.getEventType())
            .eq(LiveEvent::getMsgId, event.getMsgId()));
        return count > 0;
    }

    private List<LiveGiftRankVO> buildGiftRank(Long sessionId) {
        return userGiftStatMapper.selectList(new LambdaQueryWrapper<LiveUserGiftStat>()
                .eq(LiveUserGiftStat::getSessionId, sessionId)
                .orderByDesc(LiveUserGiftStat::getGiftValue)
                .last("limit 10"))
            .stream()
            .map(this::toGiftRank)
            .toList();
    }

    private LiveGiftRankVO toGiftRank(LiveUserGiftStat stat) {
        return LiveGiftRankVO.builder()
            .rankKey(stat.getUserKey())
            .userId(stat.getUserId())
            .douyinAccount(stat.getDouyinAccount())
            .nickname(resolveGiftRankNickname(stat))
            .giftValue(stat.getGiftValue())
            .giftCount(safeLongToInt(stat.getGiftCount()))
            .giftEventCount(safeLongToInt(stat.getGiftEventCount()))
            .build();
    }

    private String buildGiftUserKey(LiveEvent event) {
        String identityKey = firstText(event.getUserId(), event.getDouyinAccount(), event.getNickname());
        if (StringUtils.hasText(identityKey)) {
            return identityKey;
        }
        // 私密用户不会下发可识别身份，礼物榜按消息或事件隔离，避免多个匿名观众合并成同一个人。
        if (StringUtils.hasText(event.getMsgId())) {
            return "anonymous-msg:" + event.getMsgId().trim();
        }
        if (event.getId() != null) {
            return "anonymous-event:" + event.getId();
        }
        return "anonymous-session:" + event.getSessionId() + ":" + event.getEventTime();
    }

    private String resolveGiftRankNickname(LiveUserGiftStat stat) {
        return firstText(stat.getNickname(), stat.getDouyinAccount(), stat.getUserId(), "未知观众");
    }

    private long positiveSqlValue(Long value) {
        return value == null ? 0L : Math.max(value, 0L);
    }

    private long positiveSqlValue(Integer value) {
        return value == null ? 0L : Math.max(value.longValue(), 0L);
    }

    private int safeLongToInt(Long value) {
        if (value == null || value <= 0) {
            return 0;
        }
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : value.intValue();
    }

    private Map<Long, LiveAnchor> selectAnchorsByIds(List<Long> anchorIds) {
        if (anchorIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return anchorMapper.selectList(new LambdaQueryWrapper<LiveAnchor>().in(LiveAnchor::getId, anchorIds))
            .stream()
            .collect(Collectors.toMap(LiveAnchor::getId, Function.identity(), (left, right) -> left));
    }

    private LiveAnchor.AnchorProfileCommand toAnchorCommand(LiveAnchorSaveDTO dto) {
        ResolvedDouyinLiveInput resolvedInput = douyinLiveInputResolver.resolve(dto.getDouyinLiveId());
        // 主播配置只保存长期稳定的 PC live_id；room_id 属于单场直播，不能落到主播配置里。
        String liveId = looksLikeDouyinShareInput(dto.getDouyinLiveId())
            ? firstText(resolvedInput.liveId())
            : firstText(resolvedInput.liveId(), resolvedInput.canonicalInput(), dto.getDouyinLiveId());
        if (isCloudCollectEnabled(dto.getCloudCollectEnabled()) && !StringUtils.hasText(liveId)) {
            throw new BusinessException("启用云端采集时必须填写可解析的抖音 liveId 或直播分享链接");
        }
        return LiveAnchor.AnchorProfileCommand.builder()
            .anchorName(dto.getAnchorName())
            .douyinLiveId(liveId)
            .status(dto.getStatus())
            .cloudCollectEnabled(dto.getCloudCollectEnabled())
            .build();
    }

    private LiveAnchorVO toAnchorVO(LiveAnchor anchor) {
        return LiveAnchorVO.builder()
            .id(anchor.getId())
            .anchorName(anchor.getAnchorName())
            .douyinLiveId(anchor.getDouyinLiveId())
            .reportToken(anchor.getReportToken())
            .status(anchor.getStatus())
            .cloudCollectEnabled(anchor.getCloudCollectEnabled())
            .clientOnline(anchor.getClientOnline())
            .clientInstanceId(anchor.getClientInstanceId())
            .clientVersion(anchor.getClientVersion())
            .clientLastHeartbeatTime(anchor.getClientLastHeartbeatTime())
            .cloudCollecting(anchor.getCloudCollecting())
            .createTime(anchor.getCreateTime())
            .build();
    }

    private LiveSessionVO toSessionVO(LiveSession session, Map<Long, LiveAnchor> anchors) {
        LiveAnchor anchor = anchors.get(session.getAnchorId());
        return LiveSessionVO.builder()
            .id(session.getId())
            .anchorId(session.getAnchorId())
            .anchorName(anchor == null ? null : anchor.getAnchorName())
            .liveId(session.getLiveId())
            .roomId(session.getRoomId())
            .liveTitle(session.getLiveTitle())
            .status(session.getStatus())
            .activeSource(session.getActiveSource())
            .cloudTaskId(session.getCloudTaskId())
            .startTime(session.getStartTime())
            .endTime(session.getEndTime())
            .build();
    }

    private LiveCollectorTaskVO toCollectorTaskVO(LiveCollectorTask task) {
        return LiveCollectorTaskVO.builder()
            .id(task.getId())
            .sessionId(task.getSessionId())
            .anchorId(task.getAnchorId())
            .status(task.getStatus())
            .processId(task.getProcessId())
            .commandLine(task.getCommandLine())
            .lastError(task.getLastError())
            .startTime(task.getStartTime())
            .stopTime(task.getStopTime())
            .build();
    }

    private String generateReportToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean isCloudCollectEnabled(Integer value) {
        return value == null || Integer.valueOf(LiveAnchor.FLAG_YES).equals(value);
    }

    private boolean looksLikeDouyinShareInput(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalized = value.trim().toLowerCase();
        return normalized.contains("douyin.com")
            || normalized.contains("amemv.com")
            || normalized.contains("抖音")
            || normalized.length() > 80;
    }
}
