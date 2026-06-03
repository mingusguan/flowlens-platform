<script setup lang="ts">
import { onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import { dashboardApi, type DashboardOverview } from '../api/system'
import { 
  Activity, 
  BellRing, 
  Heart, 
  Play, 
  RadioTower,
  TrendingUp,
  TrendingDown,
  Users,
  Video,
  AlertTriangle
} from 'lucide-vue-next'

const overview = ref<DashboardOverview>({
  cards: [],
  trend: []
})
const chartEl = ref<HTMLDivElement>()
const rankingList = ref([
  { title: '直播切片讲解', play: 128, trend: 23 },
  { title: '新品测评混剪', play: 96, trend: 15 },
  { title: '达人二创素材', play: 72, trend: -8 },
  { title: '评论区答疑', play: 58, trend: 45 },
  { title: '教程合集', play: 43, trend: 12 }
])

const iconList = [RadioTower, Play, Heart, BellRing]
const iconTypes = ['primary', 'accent', 'warning', 'success']

onMounted(async () => {
  const { data } = await dashboardApi()
  overview.value = data
  if (chartEl.value) {
    const chart = echarts.init(chartEl.value)
    chart.setOption({
      grid: { top: 28, right: 18, bottom: 34, left: 56 },
      tooltip: { 
        trigger: 'axis',
        backgroundColor: 'rgba(17, 24, 39, 0.95)',
        borderColor: 'rgba(255, 255, 255, 0.1)',
        textStyle: { color: '#f1f5f9' }
      },
      xAxis: {
        type: 'category',
        data: data.trend.map((item) => item.day),
        axisLine: { lineStyle: { color: '#475569' } },
        axisLabel: { color: '#94a3b8', fontSize: 12 }
      },
      yAxis: [
        {
          type: 'value',
          splitLine: { lineStyle: { color: 'rgba(255,255,255,.06)' } },
          axisLabel: { color: '#94a3b8', fontSize: 12 }
        },
        {
          type: 'value',
          splitLine: { show: false },
          axisLabel: { color: '#94a3b8', fontSize: 12 }
        }
      ],
      series: [
        {
          name: '播放指数',
          type: 'line',
          smooth: true,
          data: data.trend.map((item) => item.play),
          lineStyle: { color: '#00d4aa', width: 3 },
          areaStyle: { 
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: 'rgba(0, 212, 170, 0.25)' },
              { offset: 1, color: 'rgba(0, 212, 170, 0)' }
            ]) 
          },
          symbol: 'circle',
          symbolSize: 8,
          itemStyle: { color: '#00d4aa' }
        },
        {
          name: '互动指数',
          type: 'bar',
          yAxisIndex: 1,
          data: data.trend.map((item) => item.like),
          itemStyle: { 
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: '#a855f7' },
              { offset: 1, color: '#7c3aed' }
            ]),
            borderRadius: [6, 6, 0, 0] 
          },
          barWidth: 20
        }
      ]
    })
    window.addEventListener('resize', () => chart.resize())
  }
})
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">工作台</h1>
      <p class="page-subtitle">实时监控账号矩阵流量数据</p>
    </div>

    <div class="dashboard-grid">
      <div class="stat-card" v-for="(card, index) in overview.cards" :key="card.label">
        <div class="stat-header">
          <span class="stat-label">{{ card.label }}</span>
          <div :class="['stat-icon', iconTypes[index % iconTypes.length]]">
            <component :is="iconList[index % iconList.length]" :size="20" />
          </div>
        </div>
        <div class="stat-value">{{ card.value.toLocaleString() }}</div>
        <div :class="['stat-trend', card.trend >= 0 ? 'up' : 'down']">
          <TrendingUp v-if="card.trend >= 0" :size="14" />
          <TrendingDown v-else :size="14" />
          <span>{{ card.trend >= 0 ? '+' : '' }}{{ card.trend }}%</span>
        </div>
      </div>
    </div>

    <div class="dashboard-row">
      <div class="panel large">
        <div class="panel-header">
          <div>
            <span class="panel-subtitle">Traffic Trend</span>
            <h3 class="panel-title">播放与互动趋势</h3>
          </div>
          <div class="panel-tabs">
            <button class="tab active">7天</button>
            <button class="tab">30天</button>
            <button class="tab">90天</button>
          </div>
        </div>
        <div ref="chartEl" class="chart-container"></div>
      </div>

      <div class="panel">
        <div class="panel-header">
          <div>
            <span class="panel-subtitle">Top Videos</span>
            <h3 class="panel-title">爆量视频</h3>
          </div>
        </div>
        <div class="ranking-list">
          <div class="ranking-item" v-for="(item, index) in rankingList" :key="item.title">
            <div class="rank-badge" :class="{ top: index < 3 }">{{ index + 1 }}</div>
            <div class="rank-info">
              <span class="rank-title">{{ item.title }}</span>
              <div class="rank-trend" :class="item.trend >= 0 ? 'up' : 'down'">
                {{ item.trend >= 0 ? '+' : '' }}{{ item.trend }}%
              </div>
            </div>
            <span class="rank-value">{{ item.play }}w</span>
          </div>
        </div>
      </div>
    </div>

    <div class="dashboard-row">
      <div class="panel">
        <div class="panel-header">
          <div>
            <span class="panel-subtitle">Real-time</span>
            <h3 class="panel-title">实时数据</h3>
          </div>
        </div>
        <div class="realtime-grid">
          <div class="realtime-item">
            <div class="realtime-icon">
              <Users :size="18" />
            </div>
            <div class="realtime-content">
              <span class="realtime-label">在线用户</span>
              <strong class="realtime-value">12,847</strong>
            </div>
          </div>
          <div class="realtime-item">
            <div class="realtime-icon accent">
              <Video :size="18" />
            </div>
            <div class="realtime-content">
              <span class="realtime-label">播放中</span>
              <strong class="realtime-value">2,356</strong>
            </div>
          </div>
          <div class="realtime-item">
            <div class="realtime-icon warning">
              <AlertTriangle :size="18" />
            </div>
            <div class="realtime-content">
              <span class="realtime-label">异常告警</span>
              <strong class="realtime-value">7</strong>
            </div>
          </div>
        </div>
      </div>

      <div class="panel">
        <div class="panel-header">
          <div>
            <span class="panel-subtitle">Health Score</span>
            <h3 class="panel-title">综合健康分</h3>
          </div>
        </div>
        <div class="health-score">
          <div class="score-ring">
            <div class="ring-bg"></div>
            <div class="ring-progress" style="--progress: 92.8;"></div>
            <div class="score-inner">
              <Activity :size="32" />
              <strong>92.8</strong>
              <span>健康评分</span>
            </div>
          </div>
          <div class="score-details">
            <div class="detail-item">
              <span class="detail-label">内容质量</span>
              <div class="detail-bar">
                <div class="bar-fill" style="width: 88%"></div>
              </div>
              <span class="detail-value">88%</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">互动表现</span>
              <div class="detail-bar">
                <div class="bar-fill accent" style="width: 95%"></div>
              </div>
              <span class="detail-value">95%</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">粉丝增长</span>
              <div class="detail-bar">
                <div class="bar-fill warning" style="width: 76%"></div>
              </div>
              <span class="detail-value">76%</span>
            </div>
          </div>
        </div>
      </div>

      <div class="panel">
        <div class="panel-header">
          <div>
            <span class="panel-subtitle">Quick Actions</span>
            <h3 class="panel-title">快捷操作</h3>
          </div>
        </div>
        <div class="action-grid">
          <button class="action-card">
            <div class="action-icon primary">
              <Video :size="24" />
            </div>
            <span>发布视频</span>
          </button>
          <button class="action-card">
            <div class="action-icon accent">
              <Users :size="24" />
            </div>
            <span>管理账号</span>
          </button>
          <button class="action-card">
            <div class="action-icon warning">
              <AlertTriangle :size="24" />
            </div>
            <span>查看告警</span>
          </button>
          <button class="action-card">
            <div class="action-icon success">
              <TrendingUp :size="24" />
            </div>
            <span>数据分析</span>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>