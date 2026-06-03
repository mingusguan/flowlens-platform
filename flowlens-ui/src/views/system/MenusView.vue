<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteMenuApi, listMenusApi, saveMenuApi, type MenuItem } from '../../api/system'
import { Plus, RefreshCw, Search, Trash2, Edit, ChevronRight, FolderOpen, FileText } from 'lucide-vue-next'

const loading = ref(false)
const keyword = ref('')
const menus = ref<MenuItem[]>([])
const dialogVisible = ref(false)
const form = reactive<MenuItem>({
  menuName: '',
  menuType: 'MENU',
  path: '',
  component: '',
  icon: '',
  parentId: 0,
  sortOrder: 0,
  visible: 1,
  children: undefined
})

const loadData = async () => {
  loading.value = true
  try {
    const res = await listMenusApi()
    menus.value = res.data
  } finally {
    loading.value = false
  }
}

const openDialog = (row?: MenuItem, parentId = 0) => {
  Object.assign(form, row ? { ...row } : { 
    id: undefined, 
    menuName: '', 
    menuType: 'MENU',
    path: '', 
    component: '', 
    icon: '',
    parentId, 
    sortOrder: 0, 
    visible: 1,
    children: undefined 
  })
  dialogVisible.value = true
}

const submit = async () => {
  await saveMenuApi(form)
  ElMessage.success('保存成功')
  dialogVisible.value = false
  await loadData()
}

const remove = async (row: MenuItem) => {
  if (row.children?.length) {
    ElMessage.warning('请先删除子菜单')
    return
  }
  await ElMessageBox.confirm(`确认删除菜单 ${row.menuName}？`, '删除确认', { type: 'warning' })
  await deleteMenuApi(row.id!)
  ElMessage.success('删除成功')
  await loadData()
}

const iconOptions = ['Dashboard', 'Users', 'ShieldCheck', 'Layout', 'Settings', 'FileText', 'FolderOpen', 'Activity', 'BarChart3', 'Bell', 'Settings2', 'HelpCircle']

onMounted(loadData)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">菜单管理</h1>
      <p class="page-subtitle">管理系统菜单和路由配置</p>
    </div>

    <div class="panel">
      <div class="panel-header">
        <div>
          <span class="panel-subtitle">System Menus</span>
          <h3 class="panel-title">菜单列表</h3>
        </div>
        <div class="panel-actions">
          <div class="search-input">
            <Search :size="16" />
            <input 
              v-model="keyword" 
              type="text" 
              placeholder="搜索菜单名称" 
              @keyup.enter="loadData"
            />
          </div>
          <button class="btn btn-secondary" @click="loadData">
            <RefreshCw :size="16" />
          </button>
          <button class="btn btn-primary" @click="openDialog(undefined, 0)">
            <Plus :size="16" />
            新增菜单
          </button>
        </div>
      </div>

      <div class="table-container">
        <table class="data-table">
          <thead>
            <tr>
              <th>菜单名称</th>
              <th>路由路径</th>
              <th>组件</th>
              <th>图标</th>
              <th>排序</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <template v-for="menu in menus" :key="menu.id">
              <tr>
                <td>
                  <div class="menu-info">
                    <component :is="menu.children?.length ? FolderOpen : FileText" :size="16" class="menu-icon" />
                    <span>{{ menu.menuName }}</span>
                  </div>
                </td>
                <td>{{ menu.path || '-' }}</td>
                <td>{{ menu.component || '-' }}</td>
                <td>
                  <span v-if="menu.icon" class="icon-preview">{{ menu.icon }}</span>
                  <span v-else class="text-muted">-</span>
                </td>
                <td>{{ menu.sortOrder || 0 }}</td>
                <td>
                  <span :class="['status', menu.visible === 1 ? 'active' : 'inactive']">
                    {{ menu.visible === 1 ? '启用' : '停用' }}
                  </span>
                </td>
                <td>
                  <div class="action-buttons">
                    <button 
                      v-if="!menu.children?.length"
                      class="action-btn edit" 
                      @click="openDialog(menu)"
                    >
                      <Edit :size="14" />
                      编辑
                    </button>
                    <button 
                      v-if="menu.children?.length"
                      class="action-btn add-child" 
                      @click="openDialog(undefined, menu.id)"
                    >
                      <Plus :size="14" />
                      新增子菜单
                    </button>
                    <button 
                      v-if="!menu.children?.length"
                      class="action-btn delete" 
                      @click="remove(menu)"
                    >
                      <Trash2 :size="14" />
                      删除
                    </button>
                  </div>
                </td>
              </tr>
              <template v-if="menu.children?.length">
                <tr v-for="child in menu.children" :key="child.id">
                  <td>
                    <div class="menu-info child">
                      <ChevronRight :size="14" />
                      <component :is="child.children?.length ? FolderOpen : FileText" :size="14" class="menu-icon" />
                      <span>{{ child.menuName }}</span>
                    </div>
                  </td>
                  <td>{{ child.path || '-' }}</td>
                  <td>{{ child.component || '-' }}</td>
                  <td>
                    <span v-if="child.icon" class="icon-preview">{{ child.icon }}</span>
                    <span v-else class="text-muted">-</span>
                  </td>
                  <td>{{ child.sortOrder || 0 }}</td>
                  <td>
                    <span :class="['status', child.visible === 1 ? 'active' : 'inactive']">
                      {{ child.visible === 1 ? '启用' : '停用' }}
                    </span>
                  </td>
                  <td>
                    <div class="action-buttons">
                      <button class="action-btn edit" @click="openDialog(child)">
                        <Edit :size="14" />
                        编辑
                      </button>
                      <button 
                        v-if="!child.children?.length"
                        class="action-btn delete" 
                        @click="remove(child)"
                      >
                        <Trash2 :size="14" />
                        删除
                      </button>
                    </div>
                  </td>
                </tr>
              </template>
            </template>
          </tbody>
        </table>
      </div>
    </div>

    <div class="modal-overlay" v-if="dialogVisible" @click="dialogVisible = false">
      <div class="modal" @click.stop>
        <div class="modal-header">
          <h3 class="modal-title">{{ form.id ? '编辑菜单' : '新增菜单' }}</h3>
          <button class="modal-close" @click="dialogVisible = false">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"/>
              <line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>
        <div class="modal-body">
          <div class="form-row">
            <div class="form-field-group">
              <label class="form-field-label">菜单名称</label>
              <input 
                v-model="form.menuName" 
                type="text" 
                class="form-field-control"
                placeholder="请输入菜单名称"
              />
            </div>
            <div class="form-field-group">
              <label class="form-field-label">路由路径</label>
              <input 
                v-model="form.path" 
                type="text" 
                class="form-field-control"
                placeholder="如: /dashboard"
              />
            </div>
          </div>
          <div class="form-row">
            <div class="form-field-group">
              <label class="form-field-label">组件路径</label>
              <input 
                v-model="form.component" 
                type="text" 
                class="form-field-control"
                placeholder="如: views/DashboardView.vue"
              />
            </div>
            <div class="form-field-group">
              <label class="form-field-label">排序</label>
              <input 
                v-model.number="form.sortOrder" 
                type="number" 
                class="form-field-control"
                placeholder="排序号"
              />
            </div>
          </div>
          <div class="form-field-group">
            <label class="form-field-label">图标</label>
            <div class="icon-selector">
              <button 
                v-for="icon in iconOptions" 
                :key="icon"
                :class="['icon-option', form.icon === icon && 'selected']"
                @click="form.icon = form.icon === icon ? '' : icon"
              >
                {{ icon }}
              </button>
            </div>
          </div>
          <div class="form-field-group">
            <label class="form-field-label">状态</label>
            <div class="status-switch">
              <span>停用</span>
              <button 
                :class="['switch-btn', form.visible === 1 && 'active']"
                @click="form.visible = form.visible === 1 ? 0 : 1"
              >
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
