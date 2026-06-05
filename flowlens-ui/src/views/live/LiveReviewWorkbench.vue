<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import {
  compareLiveAnchorsApi,
  compareLiveSessionsApi,
  listLiveAnchorsApi,
  listLiveSessionsApi,
  liveActiveUsersApi,
  liveEmotionsApi,
  liveReportApi,
  liveSegmentsApi,
  liveSessionStatsApi,
  liveTimelineApi,
  liveTopicsApi,
  type LiveActiveUser,
  type LiveAnchorItem,
  type LiveAnchorCompare,
  type LiveEmotionStat,
  type LiveReviewReport,
  type LiveSegment,
  type LiveSessionCompare,
  type LiveSessionItem,
  type LiveSessionStats,
  type LiveTimelineBucket,
  type LiveTopicStat
} from '../../api/system'
import { BarChart3, FileText, RefreshCw, Search } from 'lucide-vue-next'

type WorkbenchMode =
  | 'sessions'
  | 'stats'
  | 'timeline'
  | 'topics'
  | 'audience'
  | 'emotions'
  | 'segments'
  | 'report'
  | 'sessionCompare'
  | 'anchorCompare'

const props = defineProps<{
  mode: WorkbenchMode
  title: string
  subtitle: string
}>()

const loading = ref(false)
const anchors = ref<LiveAnchorItem[]>([])
const sessions = ref<LiveSessionItem[]>([])
const selectedAnchorId = ref<number>()
const selectedSessionId = ref<number>()
const stats = ref<LiveSessionStats>()
const timeline = ref<LiveTimelineBucket[]>([])
const topics = ref<LiveTopicStat[]>([])
const activeUsers = ref<LiveActiveUser[]>([])
const emotions = ref<LiveEmotionStat[]>([])
const segments = ref<LiveSegment[]>([])
const report = ref<LiveReviewReport>()
const sessionCompare = ref<LiveSessionCompare[]>([])
const anchorCompare = ref<LiveAnchorCompare[]>([])
const keyword = ref('')
const chartEl = ref<HTMLDivElement>()
let chart: echarts.ECharts | undefined
const pageSize = ref(10)
const currentPage = ref(1)
const syncingFilters = ref(false)

const selectedAnchor = computed(() => anchors.value.find((item) => item.id === selectedAnchorId.value))
const selectedSession = computed(() => sessions.value.find((item) => item.id === selectedSessionId.value))
const needsSession = computed(() => !['sessions', 'sessionCompare', 'anchorCompare'].includes(props.mode))
const needsAnchorSessionFilter = computed(() => !['anchorCompare'].includes(props.mode))

const anchorFilteredSessions = computed(() => {
  if (!selectedAnchorId.value) return sessions.value
  return sessions.value.filter((item) => item.anchorId === selectedAnchorId.value)
})

const filteredSessions = computed(() => {
  const text = keyword.value.trim()
  const source = anchorFilteredSessions.value
  if (!text) return source
  return source.filter((item) =>
    [item.anchorName, item.liveTitle, item.liveId, item.roomId].some((value) => value?.includes(text))
  )
})

const pagedSessions = computed(() => paginate(filteredSessions.value))
const pagedTimeline = computed(() => paginate(timeline.value))
const pagedTopics = computed(() => paginate(topics.value))
const pagedActiveUsers = computed(() => paginate(activeUsers.value))
const pagedEmotions = computed(() => paginate(emotions.value))
const pagedSegments = computed(() => paginate(segments.value))
const pagedSessionCompare = computed(() => paginate(sessionCompare.value))
const pagedAnchorCompare = computed(() => paginate(anchorCompare.value))

const tableTotal = computed(() => {
  if (props.mode === 'sessions') return filteredSessions.value.length
  if (props.mode === 'timeline') return timeline.value.length
  if (props.mode === 'topics') return topics.value.length
  if (props.mode === 'audience') return activeUsers.value.length
  if (props.mode === 'emotions') return emotions.value.length
  if (props.mode === 'segments') return segments.value.length
  if (props.mode === 'sessionCompare') return sessionCompare.value.length
  if (props.mode === 'anchorCompare') return anchorCompare.value.length
  return 0
})

const totalCards = computed(() => {
  const source = stats.value
  if (!source) return []
  return [
    { label: '弹幕总数', value: source.commentCount },
    { label: '活跃观众', value: source.activeUserCount },
    { label: '峰值观看', value: source.peakViewers },
    { label: '冷场片段', value: source.coldSegmentCount }
  ]
})

const loadSessions = async (anchorId = selectedAnchorId.value) => {
  const [anchorRes, sessionRes] = await Promise.all([
    listLiveAnchorsApi(),
    listLiveSessionsApi(anchorId ? { anchorId } : {})
  ])
  anchors.value = anchorRes.data || []
  sessions.value = sessionRes.data || []
  syncingFilters.value = true
  try {
    if (!selectedAnchorId.value && anchors.value.length && needsSession.value) {
      selectedAnchorId.value = anchors.value[0].id
    }
    if (needsSession.value && !selectedSessionId.value && filteredSessions.value.length) {
      selectedSessionId.value = filteredSessions.value[0].id
    }
    if (needsSession.value && selectedSessionId.value && !filteredSessions.value.some((item) => item.id === selectedSessionId.value)) {
      selectedSessionId.value = filteredSessions.value[0]?.id
    }
  } finally {
    await nextTick()
    syncingFilters.value = false
  }
}

const loadData = async () => {
  loading.value = true
  try {
    await loadSessions()
    if (props.mode === 'sessions') return
    if (props.mode === 'sessionCompare') {
      const { data } = await compareLiveSessionsApi(selectedAnchorId.value ? { anchorId: selectedAnchorId.value } : {})
      sessionCompare.value = data || []
      await renderCompareChart()
      return
    }
    if (props.mode === 'anchorCompare') {
      const { data } = await compareLiveAnchorsApi()
      anchorCompare.value = data || []
      await renderAnchorChart()
      return
    }
    if (!selectedSessionId.value) return
    const sessionId = selectedSessionId.value
    const statsRes = await liveSessionStatsApi(sessionId)
    stats.value = statsRes.data
    if (props.mode === 'stats') {
      return
    }
    if (props.mode === 'timeline') {
      const { data } = await liveTimelineApi(sessionId)
      timeline.value = data || []
      await renderTimelineChart()
    } else if (props.mode === 'topics') {
      const { data } = await liveTopicsApi(sessionId)
      topics.value = data || []
    } else if (props.mode === 'audience') {
      const { data } = await liveActiveUsersApi(sessionId)
      activeUsers.value = data || []
    } else if (props.mode === 'emotions') {
      const { data } = await liveEmotionsApi(sessionId)
      emotions.value = data || []
      await renderEmotionChart()
    } else if (props.mode === 'segments') {
      const { data } = await liveSegmentsApi(sessionId)
      segments.value = data || []
    } else if (props.mode === 'report') {
      const { data } = await liveReportApi(sessionId)
      report.value = data
      timeline.value = data?.timeline || []
      topics.value = data?.topTopics || []
      activeUsers.value = data?.activeUsers || []
      emotions.value = data?.emotions || []
      segments.value = data?.coldSegments || []
    }
  } finally {
    loading.value = false
  }
}

const renderTimelineChart = async () => {
  await nextTick()
  if (!chartEl.value) return
  chart?.dispose()
  chart = echarts.init(chartEl.value)
  chart.setOption({
    grid: { top: 28, right: 18, bottom: 34, left: 52 },
    tooltip: { trigger: 'axis' },
    legend: { textStyle: { color: '#94a3b8' } },
    xAxis: { type: 'category', data: timeline.value.map((item) => formatMinute(item.minuteTime)), axisLabel: { color: '#94a3b8' } },
    yAxis: [
      { type: 'value', axisLabel: { color: '#94a3b8' }, splitLine: { lineStyle: { color: 'rgba(255,255,255,.06)' } } },
      { type: 'value', axisLabel: { color: '#94a3b8' }, splitLine: { show: false } }
    ],
    series: [
      { name: '弹幕', type: 'bar', data: timeline.value.map((item) => item.commentCount), itemStyle: { color: '#00d4aa' } },
      { name: '在线', type: 'line', yAxisIndex: 1, smooth: true, data: timeline.value.map((item) => item.currentViewers), lineStyle: { color: '#f59e0b' } },
      { name: '活跃用户', type: 'line', smooth: true, data: timeline.value.map((item) => item.activeUserCount), lineStyle: { color: '#a855f7' } }
    ]
  })
}

const renderEmotionChart = async () => {
  await nextTick()
  if (!chartEl.value) return
  chart?.dispose()
  chart = echarts.init(chartEl.value)
  chart.setOption({
    tooltip: { trigger: 'item' },
    series: [{
      type: 'pie',
      radius: ['45%', '70%'],
      data: emotions.value.map((item) => ({ name: item.emotion, value: item.count })),
      label: { color: '#cbd5e1' }
    }]
  })
}

const renderCompareChart = async () => {
  await nextTick()
  if (!chartEl.value) return
  chart?.dispose()
  chart = echarts.init(chartEl.value)
  const rows = sessionCompare.value.slice().reverse()
  chart.setOption({
    grid: { top: 28, right: 18, bottom: 48, left: 52 },
    tooltip: { trigger: 'axis' },
    legend: { textStyle: { color: '#94a3b8' } },
    xAxis: { type: 'category', data: rows.map((item) => formatMinute(item.startTime)), axisLabel: { color: '#94a3b8' } },
    yAxis: { type: 'value', axisLabel: { color: '#94a3b8' }, splitLine: { lineStyle: { color: 'rgba(255,255,255,.06)' } } },
    series: [
      { name: '弹幕', type: 'bar', data: rows.map((item) => item.commentCount), itemStyle: { color: '#00d4aa' } },
      { name: '峰值观看', type: 'line', data: rows.map((item) => item.peakViewers), lineStyle: { color: '#f59e0b' } }
    ]
  })
}

const renderAnchorChart = async () => {
  await nextTick()
  if (!chartEl.value) return
  chart?.dispose()
  chart = echarts.init(chartEl.value)
  chart.setOption({
    grid: { top: 28, right: 18, bottom: 48, left: 52 },
    tooltip: { trigger: 'axis' },
    legend: { textStyle: { color: '#94a3b8' } },
    xAxis: { type: 'category', data: anchorCompare.value.map((item) => item.anchorName || `主播${item.anchorId}`), axisLabel: { color: '#94a3b8' } },
    yAxis: { type: 'value', axisLabel: { color: '#94a3b8' }, splitLine: { lineStyle: { color: 'rgba(255,255,255,.06)' } } },
    series: [
      { name: '总弹幕', type: 'bar', data: anchorCompare.value.map((item) => item.totalComments), itemStyle: { color: '#00d4aa' } },
      { name: '平均峰值', type: 'bar', data: anchorCompare.value.map((item) => item.avgPeakViewers), itemStyle: { color: '#a855f7' } }
    ]
  })
}

const formatMinute = (value?: string) => {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 16)
}

const formatDuration = (seconds?: number) => {
  const value = seconds || 0
  const hours = Math.floor(value / 3600)
  const minutes = Math.floor((value % 3600) / 60)
  return `${hours}h ${minutes}m`
}

watch(() => props.mode, () => {
  chart?.dispose()
  chart = undefined
  currentPage.value = 1
  loadData()
})

watch(selectedAnchorId, async () => {
  if (syncingFilters.value) return
  currentPage.value = 1
  selectedSessionId.value = undefined
  await loadData()
})

watch(selectedSessionId, () => {
  if (syncingFilters.value) return
  currentPage.value = 1
  if (needsSession.value) loadData()
})

watch(keyword, () => {
  currentPage.value = 1
})

const paginate = <T,>(items: T[]) => {
  const start = (currentPage.value - 1) * pageSize.value
  return items.slice(start, start + pageSize.value)
}

const handlePageSizeChange = (size: number) => {
  pageSize.value = size
  currentPage.value = 1
}

onMounted(() => {
  loadData()
  window.addEventListener('resize', () => chart?.resize())
})
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">{{ title }}</h1>
      <p class="page-subtitle">{{ subtitle }}</p>
    </div>

    <div class="panel">
      <div class="panel-header">
        <div>
          <span class="panel-subtitle">Review Filters</span>
          <h3 class="panel-title">{{ selectedSession?.anchorName || selectedAnchor?.anchorName || '直播复盘数据' }}</h3>
        </div>
        <div class="panel-actions">
          <select v-if="needsAnchorSessionFilter" v-model="selectedAnchorId" class="form-field-control review-select">
            <option v-if="!needsSession" :value="undefined">全部主播</option>
            <option v-for="anchor in anchors" :key="anchor.id" :value="anchor.id">
              {{ anchor.anchorName }}
            </option>
          </select>
          <select v-if="needsSession" v-model="selectedSessionId" class="form-field-control review-select">
            <option v-for="item in anchorFilteredSessions" :key="item.id" :value="item.id">
              {{ formatMinute(item.startTime) }} / {{ item.liveTitle || item.liveId || item.roomId || '未命名场次' }}
            </option>
          </select>
          <div v-if="mode === 'sessions'" class="search-input">
            <Search :size="16" />
            <input v-model="keyword" type="text" placeholder="搜索场次" />
          </div>
          <button class="btn btn-secondary" :disabled="loading" @click="loadData">
            <RefreshCw :size="16" />
            刷新
          </button>
        </div>
      </div>
    </div>

    <div v-if="stats && needsSession" class="dashboard-grid live-overview-grid">
      <div class="stat-card" v-for="card in totalCards" :key="card.label">
        <div class="stat-header">
          <span class="stat-label">{{ card.label }}</span>
          <div class="stat-icon primary"><BarChart3 :size="20" /></div>
        </div>
        <div class="stat-value">{{ card.value || 0 }}</div>
      </div>
    </div>

    <div v-if="mode === 'sessions'" class="panel">
      <div class="panel-header">
        <div>
          <span class="panel-subtitle">Sessions</span>
          <h3 class="panel-title">自动生成的直播场次</h3>
        </div>
      </div>
      <div class="table-container">
        <table class="data-table">
          <thead>
            <tr>
              <th>主播</th>
              <th>标题</th>
              <th>状态</th>
              <th>采集源</th>
              <th>直播间</th>
              <th>开始时间</th>
              <th>结束时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in pagedSessions" :key="item.id">
              <td>{{ item.anchorName || '-' }}</td>
              <td>{{ item.liveTitle || '-' }}</td>
              <td>
                <span :class="['status', item.status === 'LIVE' ? 'active' : 'inactive']">
                  {{ item.status === 'LIVE' ? '直播中' : '已结束' }}
                </span>
              </td>
              <td>{{ item.activeSource === 'CLOUD' ? '云端采集' : '等待采集' }}{{ item.cloudTaskId ? ` #${item.cloudTaskId}` : '' }}</td>
              <td>{{ item.liveId || item.roomId || '-' }}</td>
              <td>{{ formatMinute(item.startTime) }}</td>
              <td>{{ formatMinute(item.endTime) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div v-if="mode === 'stats' && stats" class="panel">
      <div class="panel-header">
        <div>
          <span class="panel-subtitle">Session Stats</span>
          <h3 class="panel-title">每场直播数据统计</h3>
        </div>
      </div>
      <div class="summary-grid">
        <div class="summary-item">
          <span>直播时长</span>
          <strong>{{ formatDuration(stats.durationSeconds) }}</strong>
        </div>
        <div class="summary-item">
          <span>平均在线</span>
          <strong>{{ stats.avgViewers }}</strong>
        </div>
        <div class="summary-item">
          <span>累计观看</span>
          <strong>{{ stats.totalViewers }}</strong>
        </div>
        <div class="summary-item">
          <span>人均弹幕</span>
          <strong>{{ stats.commentsPerUser }}</strong>
        </div>
        <div class="summary-item">
          <span>点赞汇总</span>
          <strong>{{ stats.likeCount }}</strong>
        </div>
        <div class="summary-item">
          <span>关注事件</span>
          <strong>{{ stats.followCount }}</strong>
        </div>
        <div class="summary-item">
          <span>进场事件</span>
          <strong>{{ stats.memberCount }}</strong>
        </div>
      </div>
    </div>

    <div v-if="mode === 'timeline'" class="panel">
      <div class="panel-header">
        <div>
          <span class="panel-subtitle">Timeline</span>
          <h3 class="panel-title">互动时间轴</h3>
        </div>
      </div>
      <div ref="chartEl" class="chart-container review-chart"></div>
      <div class="table-container">
        <table class="data-table">
          <thead>
            <tr><th>分钟</th><th>弹幕</th><th>活跃用户</th><th>在线</th><th>情绪</th><th>标签</th><th>关键词</th></tr>
          </thead>
          <tbody>
            <tr v-for="item in pagedTimeline" :key="item.minuteTime">
              <td>{{ formatMinute(item.minuteTime) }}</td>
              <td>{{ item.commentCount }}</td>
              <td>{{ item.activeUserCount }}</td>
              <td>{{ item.currentViewers }}</td>
              <td>{{ item.emotionSummary }}</td>
              <td>{{ item.segmentTags.join('、') || '-' }}</td>
              <td>{{ item.topKeywords.join('、') || '-' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div v-if="mode === 'topics'" class="panel">
      <div class="panel-header"><div><span class="panel-subtitle">Topics</span><h3 class="panel-title">游戏话题分析</h3></div></div>
      <div class="table-container">
        <table class="data-table">
          <thead><tr><th>话题</th><th>分类</th><th>命中</th><th>用户数</th><th>集中时间</th><th>典型弹幕</th></tr></thead>
          <tbody>
            <tr v-for="item in pagedTopics" :key="item.keyword">
              <td>{{ item.keyword }}</td>
              <td>{{ item.category }}</td>
              <td>{{ item.hitCount }}</td>
              <td>{{ item.userCount }}</td>
              <td>{{ formatMinute(item.firstTime) }} - {{ formatMinute(item.lastTime) }}</td>
              <td>{{ item.sampleComments.join(' / ') || '-' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div v-if="mode === 'audience'" class="panel">
      <div class="panel-header"><div><span class="panel-subtitle">Audience</span><h3 class="panel-title">活跃观众分析</h3></div></div>
      <div class="table-container">
        <table class="data-table">
          <thead><tr><th>观众</th><th>类型</th><th>弹幕数</th><th>参与时长</th><th>首末发言</th><th>历史场次</th><th>标记</th></tr></thead>
          <tbody>
            <tr v-for="item in pagedActiveUsers" :key="item.userKey">
              <td>{{ item.nickname || item.douyinAccount || item.userId || '-' }}</td>
              <td>{{ item.userType }}</td>
              <td>{{ item.commentCount }}</td>
              <td>{{ formatDuration(item.activeSeconds) }}</td>
              <td>{{ formatMinute(item.firstCommentTime) }} - {{ formatMinute(item.lastCommentTime) }}</td>
              <td>{{ item.historicalSessionCount }}</td>
              <td>{{ item.oldViewer ? '老观众' : item.newActiveViewer ? '新增活跃' : '-' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div v-if="mode === 'emotions'" class="panel">
      <div class="panel-header"><div><span class="panel-subtitle">Emotion</span><h3 class="panel-title">弹幕情绪与节奏</h3></div></div>
      <div ref="chartEl" class="chart-container review-chart"></div>
      <div class="table-container">
        <table class="data-table">
          <thead><tr><th>情绪</th><th>数量</th><th>占比</th><th>峰值分钟</th><th>典型弹幕</th></tr></thead>
          <tbody>
            <tr v-for="item in pagedEmotions" :key="item.emotion">
              <td>{{ item.emotion }}</td>
              <td>{{ item.count }}</td>
              <td>{{ Math.round((item.ratio || 0) * 100) }}%</td>
              <td>{{ formatMinute(item.peakMinute) }}</td>
              <td>{{ item.sampleComments.join(' / ') || '-' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div v-if="mode === 'segments'" class="panel">
      <div class="panel-header"><div><span class="panel-subtitle">Segments</span><h3 class="panel-title">冷场区间识别</h3></div></div>
      <div class="table-container">
        <table class="data-table">
          <thead><tr><th>类型</th><th>时间</th><th>时长</th><th>弹幕</th><th>活跃用户</th><th>关键词</th><th>建议</th></tr></thead>
          <tbody>
            <tr v-for="item in pagedSegments" :key="`${item.segmentType}-${item.startTime}`">
              <td>{{ item.segmentType }}</td>
              <td>{{ formatMinute(item.startTime) }} - {{ formatMinute(item.endTime) }}</td>
              <td>{{ item.durationMinutes }} 分钟</td>
              <td>{{ item.commentCount }}</td>
              <td>{{ item.activeUserCount }}</td>
              <td>{{ item.keywords.join('、') || '-' }}</td>
              <td>{{ item.suggestion }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div v-if="mode === 'report' && report" class="dashboard-row">
      <div class="panel large">
        <div class="panel-header"><div><span class="panel-subtitle">Report</span><h3 class="panel-title">单场直播复盘报告</h3></div><FileText :size="18" /></div>
        <p class="report-summary">{{ report.summary }}</p>
        <div class="review-tags">
          <span v-for="item in report.suggestions" :key="item" class="review-tag">{{ item }}</span>
        </div>
      </div>
    </div>

    <div v-if="mode === 'report' && report" class="dashboard-row">
      <div class="panel">
        <div class="panel-header"><div><span class="panel-subtitle">Topics</span><h3 class="panel-title">高频话题</h3></div></div>
        <div class="ranking-list">
          <div class="ranking-item" v-for="(item, index) in report.topTopics" :key="item.keyword">
            <div class="rank-badge" :class="{ top: index < 3 }">{{ index + 1 }}</div>
            <div class="rank-info">
              <span class="rank-title">{{ item.keyword }}</span>
              <span class="rank-trend up">{{ item.category }} / {{ item.hitCount }} 次</span>
            </div>
          </div>
        </div>
      </div>
      <div class="panel">
        <div class="panel-header"><div><span class="panel-subtitle">Audience</span><h3 class="panel-title">活跃观众 TOP</h3></div></div>
        <div class="ranking-list">
          <div class="ranking-item" v-for="(item, index) in report.activeUsers" :key="item.userKey">
            <div class="rank-badge" :class="{ top: index < 3 }">{{ index + 1 }}</div>
            <div class="rank-info">
              <span class="rank-title">{{ item.nickname }}</span>
              <span class="rank-trend up">{{ item.userType }} / {{ item.commentCount }} 条</span>
            </div>
          </div>
        </div>
      </div>
      <div class="panel">
        <div class="panel-header"><div><span class="panel-subtitle">Cold Segments</span><h3 class="panel-title">冷场片段</h3></div></div>
        <div class="ranking-list">
          <div class="ranking-item" v-for="item in report.coldSegments" :key="`${item.startTime}-${item.endTime}`">
            <div class="rank-info">
              <span class="rank-title">{{ formatMinute(item.startTime) }} - {{ formatMinute(item.endTime) }}</span>
              <span class="rank-trend down">{{ item.durationMinutes }} 分钟 / {{ item.suggestion }}</span>
            </div>
          </div>
          <div v-if="!report.coldSegments.length" class="empty-text">暂无明显冷场片段</div>
        </div>
      </div>
    </div>

    <div v-if="mode === 'sessionCompare'" class="panel">
      <div class="panel-header"><div><span class="panel-subtitle">Compare</span><h3 class="panel-title">多场直播对比</h3></div></div>
      <div ref="chartEl" class="chart-container review-chart"></div>
      <div class="table-container">
        <table class="data-table">
          <thead><tr><th>场次</th><th>主播</th><th>弹幕</th><th>峰值</th><th>活跃用户</th><th>冷场分钟</th><th>争议占比</th><th>话题</th></tr></thead>
          <tbody>
            <tr v-for="item in pagedSessionCompare" :key="item.sessionId">
              <td>{{ formatMinute(item.startTime) }}</td>
              <td>{{ item.anchorName || '-' }}</td>
              <td>{{ item.commentCount }}</td>
              <td>{{ item.peakViewers }}</td>
              <td>{{ item.activeUserCount }}</td>
              <td>{{ item.coldMinutes }}</td>
              <td>{{ Math.round((item.controversyRatio || 0) * 100) }}%</td>
              <td>{{ item.topTopics.join('、') || '-' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div v-if="mode === 'anchorCompare'" class="panel">
      <div class="panel-header"><div><span class="panel-subtitle">Compare</span><h3 class="panel-title">主播横向对比</h3></div></div>
      <div ref="chartEl" class="chart-container review-chart"></div>
      <div class="table-container">
        <table class="data-table">
          <thead><tr><th>主播</th><th>场次</th><th>总弹幕</th><th>平均峰值</th><th>活跃用户</th><th>单位在线弹幕</th><th>冷场分钟</th><th>话题</th></tr></thead>
          <tbody>
            <tr v-for="item in pagedAnchorCompare" :key="item.anchorId">
              <td>{{ item.anchorName || '-' }}</td>
              <td>{{ item.sessionCount }}</td>
              <td>{{ item.totalComments }}</td>
              <td>{{ item.avgPeakViewers }}</td>
              <td>{{ item.activeUserCount }}</td>
              <td>{{ item.commentsPerViewer }}</td>
              <td>{{ item.coldMinutes }}</td>
              <td>{{ item.topTopics.join('、') || '-' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div v-if="tableTotal > 0" class="table-pagination">
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="pageSize"
        :page-sizes="[10, 20, 50]"
        :total="tableTotal"
        background
        layout="total, sizes, prev, pager, next"
        @size-change="handlePageSizeChange"
      />
    </div>
  </div>
</template>
  type LiveAnchorItem,
