<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deleteLiveAnchorApi,
  endLiveSessionApi,
  listLiveAnchorsApi,
  listLiveSessionsApi,
  liveSummaryApi,
  saveLiveAnchorApi,
  startLiveSessionApi,
  type LiveAnchorItem,
  type LiveSessionItem,
  type LiveSummary
} from '../../api/system'
import { Copy, Edit, Play, Plus, RadioTower, RefreshCw, Search, Square, Trash2 } from 'lucide-vue-next'

const loading = ref(false)
const keyword = ref('')
const anchors = ref<LiveAnchorItem[]>([])
const sessions = ref<LiveSessionItem[]>([])
const selectedSummary = ref<LiveSummary>()
const dialogVisible = ref(false)
const startDialogVisible = ref(false)
const summaryVisible = ref(false)
const activeAnchor = ref<LiveAnchorItem>()

const form = reactive<LiveAnchorItem>({
  anchorName: '',
  douyinLiveId: '',
  status: 1,
  cloudCollectEnabled: 1
})

const startForm = reactive({
  liveId: '',
  liveTitle: ''
})

const runningSessionsByAnchor = computed(() => {
  const map = new Map<number, LiveSessionItem>()
  sessions.value
    .filter((session) => session.status === 'LIVE')
    .forEach((session) => map.set(session.anchorId, session))
  return map
})

const stats = computed(() => ({
  anchors: anchors.value.length,
  clientOnline: anchors.value.filter((anchor) => anchor.clientOnline === 1).length,
  cloudCollecting: anchors.value.filter((anchor) => anchor.cloudCollecting === 1).length,
  liveSessions: sessions.value.filter((session) => session.status === 'LIVE').length
}))

const loadData = async () => {
  loading.value = true
  try {
    const [anchorRes, sessionRes] = await Promise.all([
      listLiveAnchorsApi(keyword.value),
      listLiveSessionsApi()
    ])
    anchors.value = anchorRes.data || []
    sessions.value = sessionRes.data || []
  } finally {
    loading.value = false
  }
}

const openDialog = (row?: LiveAnchorItem) => {
  Object.assign(form, row ? { ...row } : {
    id: undefined,
    anchorName: '',
    douyinLiveId: '',
    status: 1,
    cloudCollectEnabled: 1
  })
  dialogVisible.value = true
}

const submit = async () => {
  await saveLiveAnchorApi(form)
  ElMessage.success('保存成功')
  dialogVisible.value = false
  await loadData()
}

const remove = async (row: LiveAnchorItem) => {
  await ElMessageBox.confirm(`确认删除主播 ${row.anchorName}？`, '删除确认', { type: 'warning' })
  await deleteLiveAnchorApi(row.id!)
  ElMessage.success('删除成功')
  await loadData()
}

const openStartDialog = (row: LiveAnchorItem) => {
  activeAnchor.value = row
  Object.assign(startForm, {
    liveId: row.douyinLiveId || '',
    liveTitle: ''
  })
  startDialogVisible.value = true
}

const startSession = async () => {
  if (!activeAnchor.value?.id) return
  await startLiveSessionApi(activeAnchor.value.id, startForm)
  ElMessage.success('直播场次已开启')
  startDialogVisible.value = false
  await loadData()
}

const endSession = async (row: LiveAnchorItem) => {
  const session = runningSessionsByAnchor.value.get(row.id!)
  if (!session) return
  await ElMessageBox.confirm(`确认结束 ${row.anchorName} 的当前场次？`, '结束确认', { type: 'warning' })
  await endLiveSessionApi(session.id)
  ElMessage.success('直播场次已结束')
  await loadData()
}

const showSummary = async (session: LiveSessionItem) => {
  const { data } = await liveSummaryApi(session.id)
  selectedSummary.value = data
  summaryVisible.value = true
}

const copyToken = async (token?: string) => {
  if (!token) return
  await navigator.clipboard.writeText(token)
  ElMessage.success('上报密钥已复制')
}

const sourceText = (source?: string) => {
  if (source === 'CLIENT') return '客户端'
  if (source === 'CLOUD') return '云端兜底'
  return '未接入'
}

onMounted(loadData)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">直播值班</h1>
      <p class="page-subtitle">管理主播客户端采集、云端兜底采集和直播结束统计</p>
    </div>

    <div class="dashboard-grid live-overview-grid">
      <div class="stat-card">
        <div class="stat-header">
          <span class="stat-label">主播配置</span>
          <div class="stat-icon primary"><RadioTower :size="20" /></div>
        </div>
        <div class="stat-value">{{ stats.anchors }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-header">
          <span class="stat-label">客户端在线</span>
          <div class="stat-icon success"><RadioTower :size="20" /></div>
        </div>
        <div class="stat-value">{{ stats.clientOnline }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-header">
          <span class="stat-label">云端兜底中</span>
          <div class="stat-icon warning"><RadioTower :size="20" /></div>
        </div>
        <div class="stat-value">{{ stats.cloudCollecting }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-header">
          <span class="stat-label">直播中场次</span>
          <div class="stat-icon accent"><RadioTower :size="20" /></div>
        </div>
        <div class="stat-value">{{ stats.liveSessions }}</div>
      </div>
    </div>

    <div class="panel">
      <div class="panel-header">
        <div>
          <span class="panel-subtitle">Anchors</span>
          <h3 class="panel-title">主播值班台</h3>
        </div>
        <div class="panel-actions">
          <div class="search-input">
            <Search :size="16" />
            <input v-model="keyword" type="text" placeholder="搜索主播/直播间" @keyup.enter="loadData" />
          </div>
          <button class="btn btn-secondary" @click="loadData">
            <RefreshCw :size="16" />
          </button>
          <button class="btn btn-primary" @click="openDialog()">
            <Plus :size="16" />
            新增主播
          </button>
        </div>
      </div>

      <div class="table-container">
        <table class="data-table">
          <thead>
            <tr>
              <th>主播</th>
              <th>liveId</th>
              <th>客户端</th>
              <th>采集源</th>
              <th>上报密钥</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in anchors" :key="row.id">
              <td>
                <div class="user-info-cell">
                  <div class="user-avatar-sm">{{ row.anchorName.slice(0, 1) }}</div>
                  <div>
                    <strong class="user-name">{{ row.anchorName }}</strong>
                    <span class="user-username">{{ row.clientVersion || '未上报版本' }}</span>
                  </div>
                </div>
              </td>
              <td>
                <div class="live-cell">
                  <span>{{ row.douyinLiveId || '-' }}</span>
                </div>
              </td>
              <td>
                <span :class="['status', row.clientOnline === 1 ? 'active' : 'inactive']">
                  {{ row.clientOnline === 1 ? '在线' : '离线' }}
                </span>
              </td>
              <td>{{ sourceText(runningSessionsByAnchor.get(row.id!)?.activeSource) }}</td>
              <td>
                <button class="action-btn edit token-btn" @click="copyToken(row.reportToken)">
                  <Copy :size="14" />
                  复制
                </button>
              </td>
              <td>
                <span :class="['status', row.status === 1 ? 'active' : 'inactive']">
                  {{ row.status === 1 ? '启用' : '停用' }}
                </span>
              </td>
              <td>
                <div class="action-buttons">
                  <button v-if="!runningSessionsByAnchor.get(row.id!)" class="action-btn add-child" @click="openStartDialog(row)">
                    <Play :size="14" />
                    开始
                  </button>
                  <button v-else class="action-btn delete" @click="endSession(row)">
                    <Square :size="14" />
                    结束
                  </button>
                  <button class="action-btn edit" @click="openDialog(row)">
                    <Edit :size="14" />
                    编辑
                  </button>
                  <button class="action-btn delete" @click="remove(row)">
                    <Trash2 :size="14" />
                    删除
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div class="panel">
      <div class="panel-header">
        <div>
          <span class="panel-subtitle">Sessions</span>
          <h3 class="panel-title">最近直播场次</h3>
        </div>
      </div>
      <div class="table-container">
        <table class="data-table">
          <thead>
            <tr>
              <th>主播</th>
              <th>直播间</th>
              <th>状态</th>
              <th>采集源</th>
              <th>开始时间</th>
              <th>结束时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="session in sessions" :key="session.id">
              <td>{{ session.anchorName || '-' }}</td>
              <td>{{ session.liveId || session.roomId || '-' }}</td>
              <td>
                <span :class="['status', session.status === 'LIVE' ? 'active' : 'inactive']">
                  {{ session.status === 'LIVE' ? '直播中' : '已结束' }}
                </span>
              </td>
              <td>{{ sourceText(session.activeSource) }}</td>
              <td>{{ session.startTime }}</td>
              <td>{{ session.endTime || '-' }}</td>
              <td>
                <button class="action-btn edit" @click="showSummary(session)">查看汇总</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div class="modal-overlay" v-if="dialogVisible" @click="dialogVisible = false">
      <div class="modal" @click.stop>
        <div class="modal-header">
          <h3 class="modal-title">{{ form.id ? '编辑主播' : '新增主播' }}</h3>
          <button class="modal-close" @click="dialogVisible = false">×</button>
        </div>
        <div class="modal-body">
          <div class="form-field-group">
            <label class="form-field-label">主播名称</label>
            <input v-model="form.anchorName" class="form-field-control" placeholder="例如：灵犀直播间" />
          </div>
          <div class="form-field-group">
            <label class="form-field-label">抖音直播链接/分享文案</label>
            <input v-model="form.douyinLiveId" class="form-field-control" placeholder="粘贴手机分享文案、短链或 live_id" />
          </div>
          <div class="form-field-group">
            <label class="form-field-label">云端兜底采集</label>
            <div class="status-switch">
              <span>关闭</span>
              <button :class="['switch-btn', form.cloudCollectEnabled === 1 && 'active']" @click="form.cloudCollectEnabled = form.cloudCollectEnabled === 1 ? 0 : 1">
                <span class="switch-thumb"></span>
              </button>
              <span>开启</span>
            </div>
          </div>
          <div class="form-field-group">
            <label class="form-field-label">状态</label>
            <div class="status-switch">
              <span>停用</span>
              <button :class="['switch-btn', form.status === 1 && 'active']" @click="form.status = form.status === 1 ? 0 : 1">
                <span class="switch-thumb"></span>
              </button>
              <span>启用</span>
            </div>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-secondary" @click="dialogVisible = false">取消</button>
          <button class="btn btn-primary" @click="submit">保存</button>
        </div>
      </div>
    </div>

    <div class="modal-overlay" v-if="startDialogVisible" @click="startDialogVisible = false">
      <div class="modal" @click.stop>
        <div class="modal-header">
          <h3 class="modal-title">开启直播场次</h3>
          <button class="modal-close" @click="startDialogVisible = false">×</button>
        </div>
        <div class="modal-body">
          <div class="form-field-group">
            <label class="form-field-label">场次标题</label>
            <input v-model="startForm.liveTitle" class="form-field-control" placeholder="可选" />
          </div>
          <div class="form-field-group">
            <label class="form-field-label">抖音直播链接/分享文案</label>
            <input v-model="startForm.liveId" class="form-field-control" placeholder="粘贴手机分享文案、短链或 live_id" />
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-secondary" @click="startDialogVisible = false">取消</button>
          <button class="btn btn-primary" @click="startSession">开始采集</button>
        </div>
      </div>
    </div>

    <div class="modal-overlay" v-if="summaryVisible && selectedSummary" @click="summaryVisible = false">
      <div class="modal modal-lg" @click.stop>
        <div class="modal-header">
          <h3 class="modal-title">下播汇总</h3>
          <button class="modal-close" @click="summaryVisible = false">×</button>
        </div>
        <div class="modal-body">
          <div class="summary-grid">
            <div class="summary-item">
              <span>弹幕数</span>
              <strong>{{ selectedSummary.commentCount }}</strong>
            </div>
            <div class="summary-item">
              <span>观看人数</span>
              <strong>{{ selectedSummary.viewerCount }}</strong>
            </div>
            <div class="summary-item">
              <span>点赞数</span>
              <strong>{{ selectedSummary.likeCount }}</strong>
            </div>
            <div class="summary-item">
              <span>礼物数</span>
              <strong>{{ selectedSummary.giftCount }}</strong>
            </div>
            <div class="summary-item">
              <span>礼物价值</span>
              <strong>{{ selectedSummary.giftValue }}</strong>
            </div>
          </div>
          <div class="ranking-list summary-rank">
            <div class="ranking-item" v-for="(item, index) in selectedSummary.giftRank" :key="item.rankKey || item.userId || item.douyinAccount || item.nickname">
              <div class="rank-badge" :class="{ top: index < 3 }">{{ index + 1 }}</div>
              <div class="rank-info">
                <span class="rank-title">{{ item.nickname }}</span>
                <div class="rank-trend up">{{ item.douyinAccount || item.userId || '未知账号' }} · 礼物 {{ item.giftCount }} 件</div>
              </div>
              <span class="rank-value">{{ item.giftValue }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
