package com.flowlens.admin.live.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.flowlens.admin.common.BusinessException;
import com.flowlens.admin.live.dto.LiveCompareQueryDTO;
import com.flowlens.admin.live.dto.LiveSessionQueryDTO;
import com.flowlens.admin.live.entity.LiveAnchor;
import com.flowlens.admin.live.entity.LiveEvent;
import com.flowlens.admin.live.entity.LiveSession;
import com.flowlens.admin.live.entity.LiveSessionStat;
import com.flowlens.admin.live.mapper.LiveAnchorMapper;
import com.flowlens.admin.live.mapper.LiveEventMapper;
import com.flowlens.admin.live.mapper.LiveSessionMapper;
import com.flowlens.admin.live.mapper.LiveSessionStatMapper;
import com.flowlens.admin.live.sharding.LiveEventShardTableService;
import com.flowlens.admin.live.sharding.LiveEventTableRouter;
import com.flowlens.admin.live.vo.LiveActiveUserVO;
import com.flowlens.admin.live.vo.LiveAnchorCompareVO;
import com.flowlens.admin.live.vo.LiveEmotionStatVO;
import com.flowlens.admin.live.vo.LiveReviewReportVO;
import com.flowlens.admin.live.vo.LiveSegmentVO;
import com.flowlens.admin.live.vo.LiveSessionCompareVO;
import com.flowlens.admin.live.vo.LiveSessionStatsVO;
import com.flowlens.admin.live.vo.LiveSessionVO;
import com.flowlens.admin.live.vo.LiveTimelineBucketVO;
import com.flowlens.admin.live.vo.LiveTopicStatVO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class LiveReviewService {

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    private static final Pattern CHINESE_WORD_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5]{2,8}");

    private static final Pattern ALPHA_WORD_PATTERN = Pattern.compile("[A-Za-z0-9]{3,16}");

    private static final List<KeywordRule> TOPIC_RULES = List.of(
        new KeywordRule("英雄/角色", List.of("英雄", "角色", "打野", "射手", "中路", "上路", "辅助", "法师", "刺客", "坦克")),
        new KeywordRule("地图/模式", List.of("地图", "模式", "排位", "巅峰赛", "组队", "匹配", "团战", "野区", "兵线")),
        new KeywordRule("装备/道具", List.of("装备", "出装", "铭文", "皮肤", "道具", "技能", "闪现", "惩戒")),
        new KeywordRule("上分/战绩", List.of("上分", "掉分", "连胜", "连跪", "段位", "战绩", "评分", "胜率")),
        new KeywordRule("队友/对手", List.of("队友", "对面", "路人", "主播", "老板", "车队", "演员")),
        new KeywordRule("操作/失误", List.of("操作", "失误", "意识", "走位", "预判", "手法", "细节", "节奏")),
        new KeywordRule("梗/口头禅", List.of("哈哈", "笑死", "节目效果", "名场面", "太秀", "离谱", "绷不住")),
        new KeywordRule("争议/节奏", List.of("吵", "骂", "喷", "带节奏", "质疑", "不公平", "开挂", "演员"))
    );

    private static final List<EmotionRule> EMOTION_RULES = List.of(
        new EmotionRule("兴奋", List.of("666", "牛", "秀", "厉害", "卧槽", "天秀", "漂亮", "起飞", "炸了"), 3),
        new EmotionRule("搞笑", List.of("哈哈", "笑死", "绷不住", "节目效果", "乐", "蚌埠住"), 2),
        new EmotionRule("吐槽", List.of("太菜", "这也行", "离谱", "逆天", "下饭", "无语", "看不懂"), -1),
        new EmotionRule("争议", List.of("吵", "骂", "喷", "带节奏", "质疑", "不公平", "开挂", "演员"), -2),
        new EmotionRule("负面", List.of("无聊", "没意思", "退了", "拉胯", "恶心", "别播", "难受"), -3),
        new EmotionRule("正向", List.of("支持", "加油", "好看", "喜欢", "稳", "可以", "舒服"), 1)
    );

    private final LiveAnchorMapper anchorMapper;

    private final LiveSessionMapper sessionMapper;

    private final LiveSessionStatMapper sessionStatMapper;

    private final LiveEventMapper eventMapper;

    private final LiveEventShardTableService shardTableService;

    public List<LiveSessionVO> listSessions(LiveSessionQueryDTO query) {
        List<LiveSession> sessions = selectSessions(query).stream()
            .sorted(Comparator.comparing(LiveSession::getStartTime, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
            .toList();
        Map<Long, LiveAnchor> anchors = selectAnchorsByIds(sessions.stream()
            .map(LiveSession::getAnchorId)
            .filter(Objects::nonNull)
            .distinct()
            .toList());
        return sessions.stream()
            .map(session -> toSessionVO(session, anchors.get(session.getAnchorId())))
            .toList();
    }

    public LiveSessionVO getSession(Long sessionId) {
        LiveSession session = requireSession(sessionId);
        return toSessionVO(session, anchorMapper.selectById(session.getAnchorId()));
    }

    public List<LiveSessionVO> listAnchorSessions(Long anchorId) {
        LiveSessionQueryDTO query = new LiveSessionQueryDTO();
        query.setAnchorId(anchorId);
        return listSessions(query);
    }

    public LiveSessionStatsVO getSessionStats(Long sessionId) {
        SessionDataset dataset = loadSessionDataset(sessionId);
        return buildStats(dataset, buildTimeline(dataset));
    }

    public List<LiveTimelineBucketVO> getTimeline(Long sessionId) {
        return buildTimeline(loadSessionDataset(sessionId));
    }

    public List<LiveTopicStatVO> getTopics(Long sessionId) {
        return buildTopics(loadSessionDataset(sessionId).comments());
    }

    public List<LiveActiveUserVO> getActiveUsers(Long sessionId) {
        SessionDataset dataset = loadSessionDataset(sessionId);
        return buildActiveUsers(dataset, true);
    }

    public List<LiveEmotionStatVO> getEmotions(Long sessionId) {
        return buildEmotions(loadSessionDataset(sessionId));
    }

    public List<LiveSegmentVO> getSegments(Long sessionId) {
        SessionDataset dataset = loadSessionDataset(sessionId);
        return buildSegments(dataset, buildTimeline(dataset));
    }

    public LiveReviewReportVO generateReport(Long sessionId) {
        return buildReport(sessionId);
    }

    public LiveReviewReportVO getReport(Long sessionId) {
        return buildReport(sessionId);
    }

    public List<LiveSessionCompareVO> compareSessions(LiveCompareQueryDTO query) {
        List<LiveSession> sessions = selectCompareSessions(query);
        Map<Long, LiveAnchor> anchors = selectAnchorsByIds(sessions.stream()
            .map(LiveSession::getAnchorId)
            .filter(Objects::nonNull)
            .distinct()
            .toList());
        return sessions.stream()
            .sorted(Comparator.comparing(LiveSession::getStartTime, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
            .limit(30)
            .map(session -> toSessionCompare(loadSessionDataset(session.getId()), anchors.get(session.getAnchorId())))
            .toList();
    }

    public List<LiveAnchorCompareVO> compareAnchors(LiveCompareQueryDTO query) {
        List<LiveSession> sessions = selectCompareSessions(query);
        Map<Long, List<LiveSession>> sessionsByAnchor = sessions.stream()
            .filter(session -> session.getAnchorId() != null)
            .collect(Collectors.groupingBy(LiveSession::getAnchorId));
        Map<Long, LiveAnchor> anchors = selectAnchorsByIds(new ArrayList<>(sessionsByAnchor.keySet()));
        return sessionsByAnchor.entrySet().stream()
            .map(entry -> toAnchorCompare(entry.getKey(), anchors.get(entry.getKey()), entry.getValue()))
            .sorted(Comparator.comparing(LiveAnchorCompareVO::getTotalComments, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
            .toList();
    }

    private LiveReviewReportVO buildReport(Long sessionId) {
        SessionDataset dataset = loadSessionDataset(sessionId);
        List<LiveTimelineBucketVO> timeline = buildTimeline(dataset);
        LiveSessionStatsVO stats = buildStats(dataset, timeline);
        List<LiveTopicStatVO> topics = buildTopics(dataset.comments());
        List<LiveActiveUserVO> activeUsers = buildActiveUsers(dataset, true);
        List<LiveEmotionStatVO> emotions = buildEmotions(dataset);
        List<LiveSegmentVO> coldSegments = buildSegments(dataset, timeline).stream()
            .filter(segment -> "冷场".equals(segment.getSegmentType()) || "低互动".equals(segment.getSegmentType()))
            .toList();
        List<String> suggestions = buildSuggestions(stats, topics, emotions, coldSegments);
        return LiveReviewReportVO.builder()
            .stats(stats)
            .timeline(timeline.stream().limit(120).toList())
            .topTopics(topics.stream().limit(10).toList())
            .activeUsers(activeUsers.stream().limit(10).toList())
            .emotions(emotions)
            .coldSegments(coldSegments)
            .suggestions(suggestions)
            .summary(buildSummary(stats, topics, emotions, coldSegments))
            .build();
    }

    private List<LiveTimelineBucketVO> buildTimeline(SessionDataset dataset) {
        List<LiveEvent> comments = dataset.comments();
        List<LiveEvent> statsEvents = dataset.events().stream()
            .filter(event -> LiveEvent.TYPE_ROOM_STATS.equals(event.getEventType()))
            .sorted(Comparator.comparing(LiveEvent::getEventTime))
            .toList();
        Map<LocalDateTime, BucketAccumulator> buckets = new LinkedHashMap<>();
        comments.forEach(event -> {
            LocalDateTime minute = truncateToMinute(event.getEventTime());
            BucketAccumulator bucket = buckets.computeIfAbsent(minute, BucketAccumulator::new);
            bucket.commentCount++;
            bucket.userKeys.add(buildUserKey(event));
            bucket.comments.add(event);
        });
        statsEvents.forEach(event -> {
            LocalDateTime minute = truncateToMinute(event.getEventTime());
            BucketAccumulator bucket = buckets.computeIfAbsent(minute, BucketAccumulator::new);
            bucket.currentViewers = Math.max(bucket.currentViewers, safeLong(event.getViewerCount()));
        });
        if (buckets.isEmpty()) {
            LocalDateTime start = truncateToMinute(dataset.session().getStartTime());
            buckets.put(start, new BucketAccumulator(start));
        }
        List<BucketAccumulator> ordered = buckets.values().stream()
            .sorted(Comparator.comparing(bucket -> bucket.minuteTime))
            .toList();
        double avgComments = ordered.stream().mapToLong(bucket -> bucket.commentCount).average().orElse(0);
        double avgUsers = ordered.stream().mapToLong(bucket -> bucket.userKeys.size()).average().orElse(0);
        long previousViewers = 0;
        List<LiveTimelineBucketVO> result = new ArrayList<>();
        for (BucketAccumulator bucket : ordered) {
            long viewerDelta = previousViewers == 0 ? 0 : bucket.currentViewers - previousViewers;
            if (bucket.currentViewers > 0) {
                previousViewers = bucket.currentViewers;
            }
            List<String> tags = buildBucketTags(bucket, avgComments, avgUsers, viewerDelta);
            result.add(LiveTimelineBucketVO.builder()
                .minuteTime(bucket.minuteTime)
                .commentCount(bucket.commentCount)
                .activeUserCount((long) bucket.userKeys.size())
                .currentViewers(bucket.currentViewers)
                .viewerDelta(viewerDelta)
                .topKeywords(extractKeywords(bucket.comments.stream().map(LiveEvent::getContent).toList(), 5))
                .emotionSummary(resolveMinuteEmotion(bucket.comments))
                .segmentTags(tags)
                .build());
        }
        return result;
    }

    private List<String> buildBucketTags(BucketAccumulator bucket, double avgComments, double avgUsers, long viewerDelta) {
        List<String> tags = new ArrayList<>();
        if (avgComments > 0 && bucket.commentCount >= avgComments * 2) {
            tags.add("高互动");
        }
        if (avgUsers > 0 && bucket.userKeys.size() >= avgUsers * 2) {
            tags.add("活跃用户集中");
        }
        if (avgComments > 0 && bucket.commentCount <= Math.max(1, avgComments * 0.3)) {
            tags.add("低互动");
        }
        if (viewerDelta < 0) {
            tags.add("人气下滑");
        }
        return tags;
    }

    private List<LiveTopicStatVO> buildTopics(List<LiveEvent> comments) {
        Map<String, TopicAccumulator> topics = new LinkedHashMap<>();
        comments.forEach(event -> {
            String content = event.getContent();
            if (!StringUtils.hasText(content)) {
                return;
            }
            Set<String> matched = matchTopicKeywords(content);
            if (matched.isEmpty()) {
                matched.addAll(extractKeywords(List.of(content), 6));
            }
            matched.forEach(keyword -> {
                TopicAccumulator topic = topics.computeIfAbsent(keyword, key -> new TopicAccumulator(key, resolveTopicCategory(key)));
                topic.hitCount++;
                topic.userKeys.add(buildUserKey(event));
                topic.firstTime = earlier(topic.firstTime, event.getEventTime());
                topic.lastTime = later(topic.lastTime, event.getEventTime());
                if (topic.samples.size() < 3) {
                    topic.samples.add(content);
                }
            });
        });
        return topics.values().stream()
            .sorted(Comparator.comparing(TopicAccumulator::hitCount).reversed())
            .limit(50)
            .map(topic -> LiveTopicStatVO.builder()
                .keyword(topic.keyword)
                .category(topic.category)
                .hitCount(topic.hitCount)
                .userCount((long) topic.userKeys.size())
                .firstTime(topic.firstTime)
                .lastTime(topic.lastTime)
                .sampleComments(topic.samples)
                .build())
            .toList();
    }

    private List<LiveActiveUserVO> buildActiveUsers(SessionDataset dataset, boolean includeHistory) {
        Map<String, UserAccumulator> users = new LinkedHashMap<>();
        dataset.comments().forEach(event -> {
            String userKey = buildUserKey(event);
            UserAccumulator user = users.computeIfAbsent(userKey, key -> new UserAccumulator(key));
            user.userId = firstText(user.userId, event.getUserId());
            user.douyinAccount = firstText(user.douyinAccount, event.getDouyinAccount());
            user.nickname = firstText(user.nickname, event.getNickname(), "未知观众");
            user.commentCount++;
            user.firstCommentTime = earlier(user.firstCommentTime, event.getEventTime());
            user.lastCommentTime = later(user.lastCommentTime, event.getEventTime());
            user.contents.add(event.getContent());
        });
        Map<String, Long> historicalSessions = includeHistory ? countHistoricalSessions(dataset.session(), users.keySet()) : Map.of();
        return users.values().stream()
            .sorted(Comparator.comparing(UserAccumulator::commentCount).reversed())
            .limit(100)
            .map(user -> {
                long activeSeconds = durationSeconds(user.firstCommentTime, user.lastCommentTime);
                long historicalSessionCount = historicalSessions.getOrDefault(user.userKey, 1L);
                return LiveActiveUserVO.builder()
                    .userKey(user.userKey)
                    .userId(user.userId)
                    .douyinAccount(user.douyinAccount)
                    .nickname(firstText(user.nickname, user.douyinAccount, user.userId, "未知观众"))
                    .commentCount(user.commentCount)
                    .firstCommentTime(user.firstCommentTime)
                    .lastCommentTime(user.lastCommentTime)
                    .activeSeconds(activeSeconds)
                    .userType(resolveUserType(user.contents))
                    .historicalSessionCount(historicalSessionCount)
                    .oldViewer(historicalSessionCount >= 2)
                    .newActiveViewer(historicalSessionCount <= 1 && user.commentCount >= 3)
                    .build();
            })
            .toList();
    }

    private List<LiveEmotionStatVO> buildEmotions(SessionDataset dataset) {
        Map<String, EmotionAccumulator> emotions = new LinkedHashMap<>();
        dataset.comments().forEach(event -> {
            String emotionName = resolveEmotion(event.getContent()).name();
            EmotionAccumulator emotion = emotions.computeIfAbsent(emotionName, EmotionAccumulator::new);
            emotion.count++;
            emotion.minuteCount.merge(truncateToMinute(event.getEventTime()), 1L, Long::sum);
            if (emotion.samples.size() < 5 && StringUtils.hasText(event.getContent())) {
                emotion.samples.add(event.getContent());
            }
        });
        long total = Math.max(1, dataset.comments().size());
        if (emotions.isEmpty()) {
            emotions.put("中性", new EmotionAccumulator("中性"));
        }
        return emotions.values().stream()
            .sorted(Comparator.comparing(EmotionAccumulator::count).reversed())
            .map(emotion -> LiveEmotionStatVO.builder()
                .emotion(emotion.name)
                .count(emotion.count)
                .ratio(round((double) emotion.count / total, 4))
                .peakMinute(emotion.minuteCount.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null))
                .sampleComments(emotion.samples)
                .build())
            .toList();
    }

    private List<LiveSegmentVO> buildSegments(SessionDataset dataset, List<LiveTimelineBucketVO> timeline) {
        if (timeline.isEmpty()) {
            return List.of();
        }
        double avgComments = timeline.stream().mapToLong(LiveTimelineBucketVO::getCommentCount).average().orElse(0);
        double avgUsers = timeline.stream().mapToLong(LiveTimelineBucketVO::getActiveUserCount).average().orElse(0);
        List<LiveSegmentVO> highSegments = buildContinuousSegments(dataset, timeline, "高互动",
            item -> avgComments > 0 && (safeLong(item.getCommentCount()) >= avgComments * 2 || safeLong(item.getActiveUserCount()) >= avgUsers * 2));
        List<LiveSegmentVO> lowSegments = buildContinuousSegments(dataset, timeline, "冷场",
            item -> avgComments > 0
                && safeLong(item.getCommentCount()) <= Math.max(1, avgComments * 0.3)
                && (avgUsers == 0 || safeLong(item.getActiveUserCount()) <= Math.max(1, avgUsers * 0.3)));
        List<LiveSegmentVO> viewerDropSegments = buildContinuousSegments(dataset, timeline, "人气下滑",
            item -> safeLong(item.getViewerDelta()) < 0);
        List<LiveSegmentVO> result = new ArrayList<>();
        result.addAll(highSegments);
        result.addAll(lowSegments);
        result.addAll(viewerDropSegments);
        return result.stream()
            .sorted(Comparator.comparing(LiveSegmentVO::getStartTime, Comparator.nullsLast(Comparator.naturalOrder())))
            .limit(100)
            .toList();
    }

    private List<LiveSegmentVO> buildContinuousSegments(SessionDataset dataset,
                                                        List<LiveTimelineBucketVO> timeline,
                                                        String type,
                                                        java.util.function.Predicate<LiveTimelineBucketVO> predicate) {
        List<LiveSegmentVO> segments = new ArrayList<>();
        List<LiveTimelineBucketVO> current = new ArrayList<>();
        for (LiveTimelineBucketVO bucket : timeline) {
            if (predicate.test(bucket)) {
                current.add(bucket);
                continue;
            }
            addSegmentIfPresent(dataset, segments, type, current);
            current.clear();
        }
        addSegmentIfPresent(dataset, segments, type, current);
        return segments;
    }

    private void addSegmentIfPresent(SessionDataset dataset, List<LiveSegmentVO> segments, String type, List<LiveTimelineBucketVO> buckets) {
        if (buckets.isEmpty()) {
            return;
        }
        LocalDateTime start = buckets.get(0).getMinuteTime();
        LocalDateTime end = buckets.get(buckets.size() - 1).getMinuteTime().plusMinutes(1);
        List<LiveEvent> segmentComments = dataset.comments().stream()
            .filter(event -> !event.getEventTime().isBefore(start) && event.getEventTime().isBefore(end))
            .toList();
        long commentCount = buckets.stream().mapToLong(LiveTimelineBucketVO::getCommentCount).sum();
        long activeUserCount = segmentComments.stream().map(this::buildUserKey).distinct().count();
        long viewerDelta = buckets.stream().mapToLong(bucket -> safeLong(bucket.getViewerDelta())).sum();
        segments.add(LiveSegmentVO.builder()
            .segmentType(type)
            .startTime(start)
            .endTime(end)
            .durationMinutes(Math.max(1L, Duration.between(start, end).toMinutes()))
            .commentCount(commentCount)
            .activeUserCount(activeUserCount)
            .viewerDelta(viewerDelta)
            .keywords(extractKeywords(segmentComments.stream().map(LiveEvent::getContent).toList(), 5))
            .sampleComments(segmentComments.stream().map(LiveEvent::getContent).filter(StringUtils::hasText).limit(3).toList())
            .suggestion(segmentSuggestion(type))
            .build());
    }

    private LiveSessionStatsVO buildStats(SessionDataset dataset, List<LiveTimelineBucketVO> timeline) {
        LiveSession session = dataset.session();
        LiveAnchor anchor = dataset.anchor();
        LiveSessionStat stat = sessionStatMapper.selectOne(new LambdaQueryWrapper<LiveSessionStat>()
            .eq(LiveSessionStat::getSessionId, session.getId())
            .last("limit 1"));
        long commentCount = dataset.comments().size();
        long activeUserCount = dataset.comments().stream().map(this::buildUserKey).distinct().count();
        long peakViewers = timeline.stream().mapToLong(bucket -> safeLong(bucket.getCurrentViewers())).max().orElse(safeLong(stat == null ? null : stat.getViewerCount()));
        long avgViewers = Math.round(timeline.stream()
            .mapToLong(bucket -> safeLong(bucket.getCurrentViewers()))
            .filter(value -> value > 0)
            .average()
            .orElse(0));
        long totalViewers = Math.max(peakViewers, safeLong(stat == null ? null : stat.getViewerCount()));
        long followCount = dataset.events().stream().filter(event -> LiveEvent.TYPE_FOLLOW.equals(event.getEventType())).count();
        long memberCount = dataset.events().stream().filter(event -> LiveEvent.TYPE_MEMBER.equals(event.getEventType())).count();
        List<LiveSegmentVO> segments = buildSegments(dataset, timeline);
        return LiveSessionStatsVO.builder()
            .sessionId(session.getId())
            .anchorId(session.getAnchorId())
            .anchorName(anchor == null ? null : anchor.getAnchorName())
            .liveTitle(session.getLiveTitle())
            .status(session.getStatus())
            .startTime(session.getStartTime())
            .endTime(session.getEndTime())
            .durationSeconds(durationSeconds(session.getStartTime(), firstTime(session.getEndTime(), LocalDateTime.now())))
            .peakViewers(peakViewers)
            .avgViewers(avgViewers)
            .totalViewers(totalViewers)
            .commentCount(commentCount)
            .activeUserCount(activeUserCount)
            .commentsPerUser(roundDivide(commentCount, activeUserCount))
            .likeCount(safeLong(stat == null ? null : stat.getLikeCount()))
            .followCount(followCount)
            .memberCount(memberCount)
            .highInteractionCount((int) segments.stream().filter(segment -> "高互动".equals(segment.getSegmentType())).count())
            .lowInteractionCount((int) segments.stream().filter(segment -> "低互动".equals(segment.getSegmentType())).count())
            .coldSegmentCount((int) segments.stream().filter(segment -> "冷场".equals(segment.getSegmentType())).count())
            .build();
    }

    private LiveSessionCompareVO toSessionCompare(SessionDataset dataset, LiveAnchor anchor) {
        List<LiveTimelineBucketVO> timeline = buildTimeline(dataset);
        LiveSessionStatsVO stats = buildStats(dataset, timeline);
        List<LiveTopicStatVO> topics = buildTopics(dataset.comments());
        List<LiveEmotionStatVO> emotions = buildEmotions(dataset);
        long coldMinutes = buildSegments(dataset, timeline).stream()
            .filter(segment -> "冷场".equals(segment.getSegmentType()) || "低互动".equals(segment.getSegmentType()))
            .mapToLong(segment -> safeLong(segment.getDurationMinutes()))
            .sum();
        long oldViewerCount = buildActiveUsers(dataset, true).stream()
            .filter(user -> Boolean.TRUE.equals(user.getOldViewer()))
            .count();
        double controversyRatio = emotions.stream()
            .filter(item -> "争议".equals(item.getEmotion()) || "负面".equals(item.getEmotion()))
            .mapToDouble(item -> item.getRatio() == null ? 0 : item.getRatio())
            .sum();
        return LiveSessionCompareVO.builder()
            .sessionId(dataset.session().getId())
            .anchorId(dataset.session().getAnchorId())
            .anchorName(anchor == null ? null : anchor.getAnchorName())
            .liveTitle(dataset.session().getLiveTitle())
            .startTime(dataset.session().getStartTime())
            .durationSeconds(stats.getDurationSeconds())
            .peakViewers(stats.getPeakViewers())
            .avgViewers(stats.getAvgViewers())
            .commentCount(stats.getCommentCount())
            .activeUserCount(stats.getActiveUserCount())
            .commentsPerViewer(roundDivide(stats.getCommentCount(), Math.max(1L, stats.getAvgViewers())))
            .oldViewerCount(oldViewerCount)
            .coldMinutes(coldMinutes)
            .controversyRatio(round(controversyRatio, 4))
            .topTopics(topics.stream().map(LiveTopicStatVO::getKeyword).limit(5).toList())
            .build();
    }

    private LiveAnchorCompareVO toAnchorCompare(Long anchorId, LiveAnchor anchor, List<LiveSession> sessions) {
        List<SessionDataset> datasets = sessions.stream()
            .sorted(Comparator.comparing(LiveSession::getStartTime, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
            .limit(20)
            .map(session -> loadSessionDataset(session.getId()))
            .toList();
        List<LiveSessionCompareVO> compares = datasets.stream()
            .map(dataset -> toSessionCompare(dataset, anchor))
            .toList();
        long totalDuration = compares.stream().mapToLong(item -> safeLong(item.getDurationSeconds())).sum();
        long totalComments = compares.stream().mapToLong(item -> safeLong(item.getCommentCount())).sum();
        long activeUsers = datasets.stream()
            .flatMap(dataset -> dataset.comments().stream())
            .map(this::buildUserKey)
            .distinct()
            .count();
        List<String> topTopics = datasets.stream()
            .flatMap(dataset -> buildTopics(dataset.comments()).stream())
            .collect(Collectors.groupingBy(LiveTopicStatVO::getKeyword, Collectors.summingLong(LiveTopicStatVO::getHitCount)))
            .entrySet()
            .stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(5)
            .map(Map.Entry::getKey)
            .toList();
        double controversyRatio = compares.stream().mapToDouble(item -> item.getControversyRatio() == null ? 0 : item.getControversyRatio()).average().orElse(0);
        return LiveAnchorCompareVO.builder()
            .anchorId(anchorId)
            .anchorName(anchor == null ? null : anchor.getAnchorName())
            .sessionCount((long) sessions.size())
            .totalDurationSeconds(totalDuration)
            .avgPeakViewers(Math.round(compares.stream().mapToLong(item -> safeLong(item.getPeakViewers())).average().orElse(0)))
            .avgViewers(Math.round(compares.stream().mapToLong(item -> safeLong(item.getAvgViewers())).average().orElse(0)))
            .totalComments(totalComments)
            .avgComments(Math.round(compares.stream().mapToLong(item -> safeLong(item.getCommentCount())).average().orElse(0)))
            .activeUserCount(activeUsers)
            .commentsPerViewer(roundDivide(totalComments, Math.max(1L, compares.stream().mapToLong(item -> safeLong(item.getAvgViewers())).sum())))
            .controversyRatio(round(controversyRatio, 4))
            .coldMinutes(compares.stream().mapToLong(item -> safeLong(item.getColdMinutes())).sum())
            .topTopics(topTopics)
            .build();
    }

    private SessionDataset loadSessionDataset(Long sessionId) {
        LiveSession session = requireSession(sessionId);
        LiveAnchor anchor = anchorMapper.selectById(session.getAnchorId());
        List<LiveEvent> events = selectSessionEvents(session).stream()
            .sorted(Comparator.comparing(LiveEvent::getEventTime, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
        return new SessionDataset(session, anchor, events);
    }

    private List<LiveEvent> selectSessionEvents(LiveSession session) {
        List<String> months = resolveSessionMonths(session);
        List<LiveEvent> events = new ArrayList<>();
        for (String month : months) {
            shardTableService.ensureMonthTables(month);
            LiveEventTableRouter.useMonth(month);
            try {
                events.addAll(eventMapper.selectList(new LambdaQueryWrapper<LiveEvent>()
                    .eq(LiveEvent::getSessionId, session.getId())
                    .orderByAsc(LiveEvent::getEventTime)));
            } finally {
                LiveEventTableRouter.clear();
            }
        }
        return events;
    }

    private Map<String, Long> countHistoricalSessions(LiveSession session, Set<String> userKeys) {
        if (userKeys.isEmpty()) {
            return Map.of();
        }
        List<LiveSession> sessions = sessionMapper.selectList(new LambdaQueryWrapper<LiveSession>()
            .eq(LiveSession::getAnchorId, session.getAnchorId())
            .orderByDesc(LiveSession::getStartTime)
            .last("limit 20"));
        Map<String, Set<Long>> sessionIdsByUser = new HashMap<>();
        for (LiveSession historical : sessions) {
            for (LiveEvent comment : selectSessionEvents(historical).stream()
                .filter(event -> LiveEvent.TYPE_COMMENT.equals(event.getEventType()))
                .toList()) {
                String userKey = buildUserKey(comment);
                if (userKeys.contains(userKey)) {
                    sessionIdsByUser.computeIfAbsent(userKey, key -> new HashSet<>()).add(historical.getId());
                }
            }
        }
        return sessionIdsByUser.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, entry -> (long) entry.getValue().size()));
    }

    private List<LiveSession> selectSessions(LiveSessionQueryDTO query) {
        LambdaQueryWrapper<LiveSession> wrapper = new LambdaQueryWrapper<LiveSession>()
            .orderByDesc(LiveSession::getStartTime);
        if (query != null && query.getAnchorId() != null) {
            wrapper.eq(LiveSession::getAnchorId, query.getAnchorId());
        }
        if (query != null && StringUtils.hasText(query.getStatus())) {
            wrapper.eq(LiveSession::getStatus, query.getStatus().trim().toUpperCase(Locale.ROOT));
        }
        if (query != null && query.getStartTime() != null) {
            wrapper.ge(LiveSession::getStartTime, query.getStartTime());
        }
        if (query != null && query.getEndTime() != null) {
            wrapper.le(LiveSession::getStartTime, query.getEndTime());
        }
        return sessionMapper.selectList(wrapper);
    }

    private List<LiveSession> selectCompareSessions(LiveCompareQueryDTO query) {
        LambdaQueryWrapper<LiveSession> wrapper = new LambdaQueryWrapper<LiveSession>()
            .orderByDesc(LiveSession::getStartTime);
        if (query != null && query.getAnchorId() != null) {
            wrapper.eq(LiveSession::getAnchorId, query.getAnchorId());
        }
        if (query != null && query.getAnchorIds() != null && !query.getAnchorIds().isEmpty()) {
            wrapper.in(LiveSession::getAnchorId, query.getAnchorIds());
        }
        if (query != null && query.getSessionIds() != null && !query.getSessionIds().isEmpty()) {
            wrapper.in(LiveSession::getId, query.getSessionIds());
        }
        if (query != null && query.getStartTime() != null) {
            wrapper.ge(LiveSession::getStartTime, query.getStartTime());
        }
        if (query != null && query.getEndTime() != null) {
            wrapper.le(LiveSession::getStartTime, query.getEndTime());
        }
        return sessionMapper.selectList(wrapper).stream().limit(50).toList();
    }

    private LiveSession requireSession(Long sessionId) {
        LiveSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException("直播场次不存在");
        }
        return session;
    }

    private Map<Long, LiveAnchor> selectAnchorsByIds(Collection<Long> anchorIds) {
        List<Long> ids = anchorIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return anchorMapper.selectList(new LambdaQueryWrapper<LiveAnchor>().in(LiveAnchor::getId, ids))
            .stream()
            .collect(Collectors.toMap(LiveAnchor::getId, Function.identity(), (left, right) -> left));
    }

    private List<String> resolveSessionMonths(LiveSession session) {
        LocalDateTime start = firstTime(session.getStartTime(), LocalDateTime.now());
        LocalDateTime end = firstTime(session.getEndTime(), LocalDateTime.now());
        YearMonth startMonth = YearMonth.from(start);
        YearMonth endMonth = YearMonth.from(end);
        List<String> months = new ArrayList<>();
        YearMonth cursor = startMonth;
        while (!cursor.isAfter(endMonth)) {
            months.add(cursor.format(MONTH_FORMATTER));
            cursor = cursor.plusMonths(1);
        }
        return months;
    }

    private Set<String> matchTopicKeywords(String content) {
        Set<String> keywords = new LinkedHashSet<>();
        for (KeywordRule rule : TOPIC_RULES) {
            for (String keyword : rule.keywords()) {
                if (content.contains(keyword)) {
                    keywords.add(keyword);
                }
            }
        }
        return keywords;
    }

    private String resolveTopicCategory(String keyword) {
        return TOPIC_RULES.stream()
            .filter(rule -> rule.keywords().contains(keyword))
            .map(KeywordRule::category)
            .findFirst()
            .orElse("高频词");
    }

    private List<String> extractKeywords(List<String> contents, int limit) {
        Map<String, Long> counts = new LinkedHashMap<>();
        contents.stream()
            .filter(StringUtils::hasText)
            .forEach(content -> {
                collectPatternWords(CHINESE_WORD_PATTERN, content, counts);
                collectPatternWords(ALPHA_WORD_PATTERN, content, counts);
            });
        return counts.entrySet().stream()
            .filter(entry -> !isStopWord(entry.getKey()))
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(limit)
            .map(Map.Entry::getKey)
            .toList();
    }

    private void collectPatternWords(Pattern pattern, String content, Map<String, Long> counts) {
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            String word = matcher.group().trim();
            if (word.length() >= 2) {
                counts.merge(word, 1L, Long::sum);
            }
        }
    }

    private boolean isStopWord(String word) {
        return Set.of("主播", "直播", "一下", "这个", "那个", "可以", "不是", "怎么", "什么", "哈哈").contains(word);
    }

    private EmotionRule resolveEmotion(String content) {
        if (!StringUtils.hasText(content)) {
            return new EmotionRule("中性", List.of(), 0);
        }
        return EMOTION_RULES.stream()
            .filter(rule -> rule.keywords().stream().anyMatch(content::contains))
            .findFirst()
            .orElse(new EmotionRule("中性", List.of(), 0));
    }

    private String resolveMinuteEmotion(List<LiveEvent> comments) {
        return comments.stream()
            .map(event -> resolveEmotion(event.getContent()).name())
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
            .entrySet()
            .stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("中性");
    }

    private String resolveUserType(List<String> contents) {
        long positive = contents.stream().filter(content -> resolveEmotion(content).score() > 0).count();
        long negative = contents.stream().filter(content -> resolveEmotion(content).score() < 0).count();
        long questions = contents.stream().filter(content -> StringUtils.hasText(content) && (content.contains("?") || content.contains("？"))).count();
        long memes = contents.stream().filter(content -> StringUtils.hasText(content) && (content.contains("哈哈") || content.contains("666") || content.contains("笑死"))).count();
        long controversy = contents.stream().filter(content -> "争议".equals(resolveEmotion(content).name())).count();
        if (controversy > 0 || negative >= Math.max(2, positive)) {
            return "带节奏型";
        }
        if (questions >= 2) {
            return "提问型";
        }
        if (memes >= 2) {
            return "玩梗型";
        }
        if (positive > negative) {
            return "捧场型";
        }
        return "普通互动";
    }

    private List<String> buildSuggestions(LiveSessionStatsVO stats,
                                          List<LiveTopicStatVO> topics,
                                          List<LiveEmotionStatVO> emotions,
                                          List<LiveSegmentVO> coldSegments) {
        List<String> suggestions = new ArrayList<>();
        if (safeLong(stats.getCommentCount()) == 0) {
            suggestions.add("本场弹幕量不足，先确认云端采集是否稳定，再做复盘判断。");
        }
        if (!topics.isEmpty()) {
            suggestions.add("下场可以围绕「" + topics.get(0).getKeyword() + "」延展互动问题，提高观众参与度。");
        }
        if (!coldSegments.isEmpty()) {
            suggestions.add("冷场集中在 " + coldSegments.size() + " 个片段，可在这些时间点安排投票、组队或战绩提问。");
        }
        double negativeRatio = emotions.stream()
            .filter(item -> "负面".equals(item.getEmotion()) || "争议".equals(item.getEmotion()))
            .mapToDouble(item -> item.getRatio() == null ? 0 : item.getRatio())
            .sum();
        if (negativeRatio >= 0.25) {
            suggestions.add("争议或负面弹幕占比较高，下场需要提前准备控节奏话题和解释口径。");
        }
        if (suggestions.isEmpty()) {
            suggestions.add("本场互动节奏平稳，下场优先复用高频话题并观察老观众回访。");
        }
        return suggestions;
    }

    private String buildSummary(LiveSessionStatsVO stats,
                                List<LiveTopicStatVO> topics,
                                List<LiveEmotionStatVO> emotions,
                                List<LiveSegmentVO> coldSegments) {
        String topic = topics.isEmpty() ? "暂无明显高频话题" : topics.get(0).getKeyword();
        String emotion = emotions.isEmpty() ? "中性" : emotions.get(0).getEmotion();
        return "本场弹幕 " + safeLong(stats.getCommentCount())
            + " 条，活跃观众 " + safeLong(stats.getActiveUserCount())
            + " 人，峰值观看 " + safeLong(stats.getPeakViewers())
            + "；主要话题为「" + topic + "」，主情绪为「" + emotion + "」，识别冷场片段 "
            + coldSegments.size() + " 个。";
    }

    private String segmentSuggestion(String type) {
        if ("高互动".equals(type)) {
            return "保留这一段的互动触发方式，下场可复用同类话题。";
        }
        if ("人气下滑".equals(type)) {
            return "观察下滑前后的弹幕关键词，及时切换互动问题或游戏目标。";
        }
        return "低互动时段建议提前安排弹幕问题、组队邀请或局内目标提示。";
    }

    private LiveSessionVO toSessionVO(LiveSession session, LiveAnchor anchor) {
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

    private String buildUserKey(LiveEvent event) {
        return firstText(event.getUserId(), event.getDouyinAccount(), event.getNickname(), "anonymous:" + event.getId());
    }

    private LocalDateTime truncateToMinute(LocalDateTime time) {
        LocalDateTime resolved = firstTime(time, LocalDateTime.now());
        return resolved.withSecond(0).withNano(0);
    }

    private LocalDateTime earlier(LocalDateTime left, LocalDateTime right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isBefore(right) ? left : right;
    }

    private LocalDateTime later(LocalDateTime left, LocalDateTime right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isAfter(right) ? left : right;
    }

    private LocalDateTime firstTime(LocalDateTime first, LocalDateTime second) {
        return first == null ? second : first;
    }

    private long durationSeconds(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || end.isBefore(start)) {
            return 0L;
        }
        return Duration.between(start, end).toSeconds();
    }

    private long safeLong(Long value) {
        return value == null ? 0L : Math.max(0L, value);
    }

    private double roundDivide(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0D;
        }
        return round((double) numerator / denominator, 2);
    }

    private double round(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private record SessionDataset(LiveSession session, LiveAnchor anchor, List<LiveEvent> events) {

        private List<LiveEvent> comments() {
            return events.stream()
                .filter(event -> LiveEvent.TYPE_COMMENT.equals(event.getEventType()))
                .toList();
        }
    }

    private record KeywordRule(String category, List<String> keywords) {
    }

    private record EmotionRule(String name, List<String> keywords, int score) {
    }

    private static final class BucketAccumulator {

        private final LocalDateTime minuteTime;

        private long commentCount;

        private long currentViewers;

        private final Set<String> userKeys = new HashSet<>();

        private final List<LiveEvent> comments = new ArrayList<>();

        private BucketAccumulator(LocalDateTime minuteTime) {
            this.minuteTime = minuteTime;
        }
    }

    private static final class TopicAccumulator {

        private final String keyword;

        private final String category;

        private long hitCount;

        private LocalDateTime firstTime;

        private LocalDateTime lastTime;

        private final Set<String> userKeys = new HashSet<>();

        private final List<String> samples = new ArrayList<>();

        private TopicAccumulator(String keyword, String category) {
            this.keyword = keyword;
            this.category = category;
        }

        private long hitCount() {
            return hitCount;
        }
    }

    private static final class UserAccumulator {

        private final String userKey;

        private String userId;

        private String douyinAccount;

        private String nickname;

        private long commentCount;

        private LocalDateTime firstCommentTime;

        private LocalDateTime lastCommentTime;

        private final List<String> contents = new ArrayList<>();

        private UserAccumulator(String userKey) {
            this.userKey = userKey;
        }

        private long commentCount() {
            return commentCount;
        }
    }

    private static final class EmotionAccumulator {

        private final String name;

        private long count;

        private final Map<LocalDateTime, Long> minuteCount = new HashMap<>();

        private final List<String> samples = new ArrayList<>();

        private EmotionAccumulator(String name) {
            this.name = name;
        }

        private long count() {
            return count;
        }
    }
}
