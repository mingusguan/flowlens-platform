<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deleteLiveAnchorApi,
  listLiveAnchorsApi,
  listLiveSessionsApi,
  saveLiveAnchorApi,
  type LiveAnchorItem,
  type LiveSessionItem
} from '../../api/system'
import { Edit, Plus, RadioTower, RefreshCw, Search, Trash2 } from 'lucide-vue-next'

const loading = ref(false)
const keyword = ref('')
const anchors = ref<LiveAnchorItem[]>([])
const sessions = ref<LiveSessionItem[]>([])
const dialogVisible = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)

const form = reactive<LiveAnchorItem>({
  anchorName: '',
  douyinLiveId: '',
  status: 1,
  cloudCollectEnabled: 1
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
  monitoring: anchors.value.filter((anchor) => anchor.cloudCollectEnabled === 1 && anchor.status === 1).length,
  collecting: anchors.value.filter((anchor) => anchor.cloudCollecting === 1).length,
  liveSessions: sessions.value.filter((session) => session.status === 'LIVE').length
}))

const pagedAnchors = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return anchors.value.slice(start, start + pageSize.value)
})

const loadData = async () => {
  loading.value = true
  currentPage.value = 1
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

const handlePageSizeChange = (size: number) => {
  pageSize.value = size
  currentPage.value = 1
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

const sourceText = (source?: string) => {
  if (source === 'CLOUD') return '云端监听'
  return '等待监听'
}

const monitorText = (row: LiveAnchorItem) => {
  if (row.status !== 1) return '已停用'
  return row.cloudCollectEnabled === 1 ? '监听中' : '未监听'
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">主播监听</h1>
      <p class="page-subtitle">创建主播档案后，云端自动监听开播并生成直播场次</p>
    </div>

    <div class="dashboard-grid live-overview-grid">
      <div class="stat-card">
        <div class="stat-header">
          <span class="stat-label">主播档案</span>
          <div class="stat-icon primary"><RadioTower :size="20" /></div>
        </div>
        <div class="stat-value">{{ stats.anchors }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-header">
          <span class="stat-label">启用监听</span>
          <div class="stat-icon success"><RadioTower :size="20" /></div>
        </div>
        <div class="stat-value">{{ stats.monitoring }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-header">
          <span class="stat-label">云端采集中</span>
          <div class="stat-icon warning"><RadioTower :size="20" /></div>
        </div>
        <div class="stat-value">{{ stats.collecting }}</div>
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
          <h3 class="panel-title">主播监听配置</h3>
        </div>
        <div class="panel-actions">
          <div class="search-input">
            <Search :size="16" />
            <input v-model="keyword" type="text" placeholder="搜索主播/liveId" @keyup.enter="loadData" />
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
              <th>监听</th>
              <th>当前场次</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in pagedAnchors" :key="row.id">
              <td>
                <div class="user-info-cell">
                  <div class="user-avatar-sm">{{ row.anchorName.slice(0, 1) }}</div>
                  <div>
                    <strong class="user-name">{{ row.anchorName }}</strong>
                    <span class="user-username">{{ row.douyinLiveId || '未填写 liveId' }}</span>
                  </div>
                </div>
              </td>
              <td>{{ row.douyinLiveId || '-' }}</td>
              <td>
                <span :class="['status', row.cloudCollectEnabled === 1 && row.status === 1 ? 'active' : 'inactive']">
                  {{ monitorText(row) }}
                </span>
              </td>
              <td>{{ sourceText(runningSessionsByAnchor.get(row.id!)?.activeSource) }}</td>
              <td>
                <span :class="['status', row.status === 1 ? 'active' : 'inactive']">
                  {{ row.status === 1 ? '启用' : '停用' }}
                </span>
              </td>
              <td>
                <div class="action-buttons">
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
            <tr v-if="!anchors.length">
              <td colspan="6">暂无主播配置</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="anchors.length" class="table-pagination">
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          :total="anchors.length"
          background
          layout="total, sizes, prev, pager, next"
          @size-change="handlePageSizeChange"
        />
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
            <input v-model="form.anchorName" class="form-field-control" placeholder="例如：桃丸丸" />
          </div>
          <div class="form-field-group">
            <label class="form-field-label">抖音直播链接/分享文案</label>
            <input v-model="form.douyinLiveId" class="form-field-control" placeholder="粘贴直播间链接、分享文案或 liveId" />
          </div>
          <div class="form-field-group">
            <label class="form-field-label">云端自动监听</label>
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
  </div>
</template>
