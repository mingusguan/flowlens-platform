<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ChevronLeft, LogOut, RadioTower, RefreshCw, Save, Search, X } from 'lucide-vue-next'
import { listLiveAnchorsApi, saveLiveAnchorApi, type LiveAnchorItem } from '../../api/system'
import { useAuthStore } from '../../stores/auth'

const router = useRouter()
const auth = useAuthStore()

const loading = ref(false)
const saving = ref(false)
const keyword = ref('')
const anchors = ref<LiveAnchorItem[]>([])
const activeAnchorId = ref<number>()

const form = reactive({
  liveInput: '',
  cloudCollectEnabled: 1
})

const activeAnchor = computed(() => anchors.value.find((anchor) => anchor.id === activeAnchorId.value))

const filteredAnchors = computed(() => {
  const normalizedKeyword = keyword.value.trim().toLowerCase()
  if (!normalizedKeyword) {
    return anchors.value
  }
  return anchors.value.filter((anchor) => {
    const name = anchor.anchorName.toLowerCase()
    const liveId = (anchor.douyinLiveId || '').toLowerCase()
    return name.includes(normalizedKeyword) || liveId.includes(normalizedKeyword)
  })
})

const selectAnchor = (anchor: LiveAnchorItem) => {
  activeAnchorId.value = anchor.id
  form.liveInput = anchor.douyinLiveId || ''
  form.cloudCollectEnabled = anchor.cloudCollectEnabled ?? 1
}

const loadData = async () => {
  loading.value = true
  try {
    const { data } = await listLiveAnchorsApi('')
    anchors.value = data || []
    if (!activeAnchorId.value && anchors.value.length > 0) {
      selectAnchor(anchors.value[0])
    } else if (activeAnchorId.value && !anchors.value.some((anchor) => anchor.id === activeAnchorId.value)) {
      activeAnchorId.value = undefined
    }
  } finally {
    loading.value = false
  }
}

const clearInput = () => {
  form.liveInput = ''
}

const saveLiveInput = async () => {
  if (!activeAnchor.value) {
    ElMessage.warning('请先选择主播')
    return
  }
  if (form.cloudCollectEnabled === 1 && !form.liveInput.trim()) {
    ElMessage.warning('开启云端兜底时需要填写直播链接或 liveId')
    return
  }
  saving.value = true
  try {
    const payload: LiveAnchorItem = {
      ...activeAnchor.value,
      douyinLiveId: form.liveInput.trim(),
      cloudCollectEnabled: form.cloudCollectEnabled
    }
    const { data } = await saveLiveAnchorApi(payload)
    const index = anchors.value.findIndex((anchor) => anchor.id === data.id)
    if (index >= 0) {
      anchors.value.splice(index, 1, data)
    }
    selectAnchor(data)
    ElMessage.success('已保存直播链接')
  } finally {
    saving.value = false
  }
}

const goDesktop = () => {
  router.push('/live/anchors')
}

const logout = () => {
  auth.logout()
  router.replace({
    path: '/login',
    query: { redirect: '/mobile/live-input' }
  })
}

onMounted(loadData)
</script>

<template>
  <main class="mobile-live-page">
    <header class="mobile-live-header">
      <div>
        <span class="mobile-eyebrow">FlowLens Live</span>
        <h1>手机录入直播链接</h1>
      </div>
      <button class="icon-button" type="button" title="退出登录" @click="logout">
        <LogOut :size="18" />
      </button>
    </header>

    <section class="mobile-live-toolbar">
      <div class="mobile-search">
        <Search :size="18" />
        <input v-model="keyword" type="search" placeholder="搜索主播或 liveId" />
      </div>
      <button class="icon-button" type="button" title="刷新" :disabled="loading" @click="loadData">
        <RefreshCw :size="18" :class="{ spinning: loading }" />
      </button>
    </section>

    <section class="anchor-strip" aria-label="主播列表">
      <button
        v-for="anchor in filteredAnchors"
        :key="anchor.id"
        :class="['anchor-chip', activeAnchorId === anchor.id && 'active']"
        type="button"
        @click="selectAnchor(anchor)"
      >
        <span class="anchor-avatar">{{ anchor.anchorName.slice(0, 1) }}</span>
        <span class="anchor-chip-text">
          <strong>{{ anchor.anchorName }}</strong>
          <small>{{ anchor.douyinLiveId || '未填写 liveId' }}</small>
        </span>
      </button>
      <div v-if="!loading && filteredAnchors.length === 0" class="empty-hint">没有匹配的主播</div>
    </section>

    <section v-if="activeAnchor" class="mobile-live-form">
      <div class="selected-anchor">
        <div class="selected-avatar">{{ activeAnchor.anchorName.slice(0, 1) }}</div>
        <div>
          <span class="selected-label">当前主播</span>
          <strong>{{ activeAnchor.anchorName }}</strong>
        </div>
      </div>

      <div class="field-block">
        <label for="live-input">抖音直播链接/分享文案</label>
        <div class="textarea-wrap">
          <textarea
            id="live-input"
            v-model="form.liveInput"
            rows="7"
            placeholder="从抖音手机端复制直播间分享文案后粘贴到这里，也可以直接填 live_id"
          ></textarea>
          <button v-if="form.liveInput" class="clear-button" type="button" title="清空" @click="clearInput">
            <X :size="16" />
          </button>
        </div>
      </div>

      <div class="mobile-status-row">
        <div>
          <span>云端兜底采集</span>
          <small>{{ form.cloudCollectEnabled === 1 ? '开启后会用保存的 liveId 自动探测开播' : '关闭后只保存主播配置' }}</small>
        </div>
        <button
          :class="['mobile-switch', form.cloudCollectEnabled === 1 && 'active']"
          type="button"
          @click="form.cloudCollectEnabled = form.cloudCollectEnabled === 1 ? 0 : 1"
        >
          <span></span>
        </button>
      </div>

      <dl class="mobile-meta">
        <div>
          <dt>当前保存</dt>
          <dd>{{ activeAnchor.douyinLiveId || '-' }}</dd>
        </div>
        <div>
          <dt>客户端</dt>
          <dd>{{ activeAnchor.clientOnline === 1 ? '在线' : '离线' }}</dd>
        </div>
      </dl>

      <button class="save-button" type="button" :disabled="saving" @click="saveLiveInput">
        <span v-if="saving" class="button-loader"></span>
        <Save v-else :size="18" />
        保存直播链接
      </button>
    </section>

    <section v-else class="mobile-live-form empty-state">
      <RadioTower :size="32" />
      <strong>{{ loading ? '正在加载主播' : '暂无主播配置' }}</strong>
      <span>{{ loading ? '稍等一下' : '请先在 PC 端创建主播，再用手机录入直播链接' }}</span>
    </section>

    <button class="desktop-link" type="button" @click="goDesktop">
      <ChevronLeft :size="16" />
      返回直播值班台
    </button>
  </main>
</template>

<style scoped>
.mobile-live-page {
  min-height: 100vh;
  padding: 18px 16px 30px;
  background:
    linear-gradient(145deg, rgba(0, 212, 170, 0.08), transparent 34%),
    linear-gradient(215deg, rgba(168, 85, 247, 0.08), transparent 36%),
    var(--color-bg-primary);
  color: var(--color-text-primary);
}

.mobile-live-header,
.mobile-live-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  max-width: 560px;
  margin: 0 auto;
}

.mobile-live-header {
  padding: 6px 0 18px;
}

.mobile-eyebrow {
  display: block;
  margin-bottom: 4px;
  color: var(--color-primary);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.6px;
  text-transform: uppercase;
}

.mobile-live-header h1 {
  margin: 0;
  font-size: 24px;
  line-height: 1.2;
}

.icon-button {
  width: 42px;
  height: 42px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.06);
  color: var(--color-text-primary);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
}

.icon-button:disabled {
  opacity: 0.6;
}

.mobile-live-toolbar {
  margin-bottom: 14px;
}

.mobile-search {
  min-width: 0;
  flex: 1;
  height: 44px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.05);
  color: var(--color-text-muted);
}

.mobile-search input {
  min-width: 0;
  flex: 1;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--color-text-primary);
  font-size: 15px;
}

.anchor-strip {
  max-width: 560px;
  margin: 0 auto 14px;
  display: flex;
  gap: 10px;
  overflow-x: auto;
  padding-bottom: 4px;
}

.anchor-chip {
  min-width: 180px;
  max-width: 220px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.05);
  color: var(--color-text-primary);
  text-align: left;
}

.anchor-chip.active {
  border-color: rgba(0, 212, 170, 0.55);
  background: rgba(0, 212, 170, 0.12);
}

.anchor-avatar,
.selected-avatar {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  background: linear-gradient(135deg, rgba(0, 212, 170, 0.28), rgba(168, 85, 247, 0.22));
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--color-primary);
  font-weight: 800;
  flex: 0 0 auto;
}

.anchor-chip-text {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.anchor-chip-text strong,
.anchor-chip-text small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.anchor-chip-text strong {
  font-size: 14px;
}

.anchor-chip-text small {
  color: var(--color-text-muted);
  font-size: 12px;
}

.mobile-live-form {
  max-width: 560px;
  margin: 0 auto;
  padding: 18px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: rgba(17, 24, 39, 0.84);
}

.selected-anchor {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 18px;
}

.selected-avatar {
  width: 44px;
  height: 44px;
}

.selected-label {
  display: block;
  margin-bottom: 3px;
  color: var(--color-text-muted);
  font-size: 12px;
}

.selected-anchor strong {
  font-size: 18px;
}

.field-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.field-block label,
.mobile-status-row span {
  color: var(--color-text-secondary);
  font-size: 13px;
  font-weight: 600;
}

.textarea-wrap {
  position: relative;
}

.textarea-wrap textarea {
  width: 100%;
  min-height: 150px;
  padding: 12px 42px 12px 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  outline: 0;
  resize: vertical;
  background: rgba(255, 255, 255, 0.05);
  color: var(--color-text-primary);
  font-size: 15px;
  line-height: 1.55;
}

.textarea-wrap textarea:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(0, 212, 170, 0.1);
}

.clear-button {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 30px;
  height: 30px;
  border: 0;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.08);
  color: var(--color-text-secondary);
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.mobile-status-row {
  margin: 16px 0;
  padding: 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.04);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.mobile-status-row small {
  display: block;
  margin-top: 4px;
  color: var(--color-text-muted);
  font-size: 12px;
  line-height: 1.45;
}

.mobile-switch {
  width: 48px;
  height: 28px;
  border: 0;
  border-radius: 999px;
  padding: 2px;
  background: rgba(255, 255, 255, 0.14);
  transition: background 0.2s ease;
  flex: 0 0 auto;
}

.mobile-switch span {
  display: block;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: #fff;
  transition: transform 0.2s ease;
}

.mobile-switch.active {
  background: var(--color-primary);
}

.mobile-switch.active span {
  transform: translateX(20px);
}

.mobile-meta {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin: 0 0 18px;
}

.mobile-meta div {
  min-width: 0;
  padding: 12px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.04);
}

.mobile-meta dt {
  color: var(--color-text-muted);
  font-size: 12px;
  margin-bottom: 6px;
}

.mobile-meta dd {
  margin: 0;
  color: var(--color-text-primary);
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.save-button {
  width: 100%;
  height: 48px;
  border: 0;
  border-radius: 8px;
  background: linear-gradient(135deg, #00d4aa, #22d3ee);
  color: var(--color-bg-primary);
  font-size: 15px;
  font-weight: 800;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.save-button:disabled {
  opacity: 0.7;
}

.button-loader {
  width: 18px;
  height: 18px;
  border: 2px solid rgba(10, 14, 23, 0.25);
  border-top-color: var(--color-bg-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.empty-state {
  min-height: 220px;
  align-items: center;
  justify-content: center;
  text-align: center;
  display: flex;
  flex-direction: column;
  gap: 10px;
  color: var(--color-text-secondary);
}

.empty-state svg {
  color: var(--color-primary);
}

.empty-state strong {
  color: var(--color-text-primary);
}

.empty-state span,
.empty-hint {
  color: var(--color-text-muted);
  font-size: 13px;
}

.empty-hint {
  padding: 14px 2px;
}

.desktop-link {
  max-width: 560px;
  margin: 14px auto 0;
  height: 42px;
  width: 100%;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.05);
  color: var(--color-text-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 14px;
}

.spinning {
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 420px) {
  .mobile-live-page {
    padding-inline: 12px;
  }

  .mobile-live-header h1 {
    font-size: 21px;
  }

  .mobile-meta {
    grid-template-columns: 1fr;
  }
}
</style>
