<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteRoleApi, listMenusApi, listRolesApi, saveRoleApi, type MenuItem, type RoleItem } from '../../api/system'
import { Plus, RefreshCw, Search, Trash2, Edit, ShieldCheck } from 'lucide-vue-next'

const loading = ref(false)
const keyword = ref('')
const roles = ref<RoleItem[]>([])
const menus = ref<MenuItem[]>([])
const dialogVisible = ref(false)
const expandedKeys = ref<number[]>([])
const form = reactive<RoleItem>({
  roleName: '',
  roleKey: '',
  status: 1,
  menuIds: []
})

const normalizeRole = (role: RoleItem): RoleItem => ({
  ...role,
  menuIds: Array.isArray(role.menuIds) ? role.menuIds : []
})

const normalizeMenu = (menu: MenuItem): MenuItem => ({
  ...menu,
  children: (menu.children || []).map(normalizeMenu)
})

const loadData = async () => {
  loading.value = true
  try {
    const [roleRes, menuRes] = await Promise.all([listRolesApi(keyword.value), listMenusApi()])
    roles.value = (roleRes.data || []).map(normalizeRole)
    menus.value = (menuRes.data || []).map(normalizeMenu)
  } finally {
    loading.value = false
  }
}

const openDialog = (row?: RoleItem) => {
  Object.assign(form, row ? normalizeRole(row) : { id: undefined, roleName: '', roleKey: '', status: 1, menuIds: [] })
  dialogVisible.value = true
}

const submit = async () => {
  await saveRoleApi(form)
  ElMessage.success('保存成功')
  dialogVisible.value = false
  await loadData()
}

const remove = async (row: RoleItem) => {
  await ElMessageBox.confirm(`确认删除角色 ${row.roleName}？`, '删除确认', { type: 'warning' })
  await deleteRoleApi(row.id!)
  ElMessage.success('删除成功')
  await loadData()
}

const toggleMenu = (menuId: number) => {
  if (!Array.isArray(form.menuIds)) {
    form.menuIds = []
  }
  const index = form.menuIds.indexOf(menuId)
  if (index > -1) {
    form.menuIds.splice(index, 1)
  } else {
    form.menuIds.push(menuId)
  }
}

const toggleExpand = (menuId: number) => {
  const index = expandedKeys.value.indexOf(menuId)
  if (index > -1) {
    expandedKeys.value.splice(index, 1)
  } else {
    expandedKeys.value.push(menuId)
  }
}

const isExpanded = (menuId: number) => expandedKeys.value.includes(menuId)
const isChecked = (menuId: number) => form.menuIds.includes(menuId)
const menuChecked = (menu: MenuItem) => menu.id !== undefined && isChecked(menu.id)
const menuExpanded = (menu: MenuItem) => menu.id !== undefined && isExpanded(menu.id)
const toggleMenuNode = (menu: MenuItem) => {
  if (menu.id !== undefined) {
    toggleMenu(menu.id)
  }
}
const toggleExpandNode = (menu: MenuItem) => {
  if (menu.id !== undefined) {
    toggleExpand(menu.id)
  }
}

onMounted(loadData)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">角色管理</h1>
      <p class="page-subtitle">管理系统角色和权限分配</p>
    </div>

    <div class="panel">
      <div class="panel-header">
        <div>
          <span class="panel-subtitle">System Roles</span>
          <h3 class="panel-title">角色列表</h3>
        </div>
        <div class="panel-actions">
          <div class="search-input">
            <Search :size="16" />
            <input 
              v-model="keyword" 
              type="text" 
              placeholder="搜索角色" 
              @keyup.enter="loadData"
            />
          </div>
          <button class="btn btn-secondary" @click="loadData">
            <RefreshCw :size="16" />
          </button>
          <button class="btn btn-primary" @click="openDialog()">
            <Plus :size="16" />
            新增角色
          </button>
        </div>
      </div>

      <div class="table-container">
        <table class="data-table">
          <thead>
            <tr>
              <th>角色名称</th>
              <th>角色标识</th>
              <th>权限数量</th>
              <th>状态</th>
              <th>创建时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in roles" :key="row.id">
              <td>
                <div class="role-info">
                  <ShieldCheck :size="16" class="role-icon" />
                  <span>{{ row.roleName }}</span>
                </div>
              </td>
              <td>
                <code class="role-key">{{ row.roleKey }}</code>
              </td>
              <td>{{ row.menuIds?.length || 0 }}</td>
              <td>
                <span :class="['status', row.status === 1 ? 'active' : 'inactive']">
                  {{ row.status === 1 ? '启用' : '停用' }}
                </span>
              </td>
              <td>{{ row.createTime || '-' }}</td>
              <td>
                <div class="action-buttons">
                  <button class="action-btn edit" @click="openDialog(row)">
                    <Edit :size="14" />
                    编辑
                  </button>
                  <button 
                    class="action-btn delete" 
                    :disabled="row.id === 1"
                    @click="remove(row)"
                  >
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

    <div class="modal-overlay" v-if="dialogVisible" @click="dialogVisible = false">
      <div class="modal modal-lg" @click.stop>
        <div class="modal-header">
          <h3 class="modal-title">{{ form.id ? '编辑角色' : '新增角色' }}</h3>
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
              <label class="form-field-label">角色名称</label>
              <input 
                v-model="form.roleName" 
                type="text" 
                class="form-field-control"
                placeholder="请输入角色名称"
              />
            </div>
            <div class="form-field-group">
              <label class="form-field-label">角色标识</label>
              <input 
                v-model="form.roleKey" 
                type="text" 
                class="form-field-control"
                placeholder="如: admin, editor"
              />
            </div>
          </div>
          <div class="form-field-group">
            <label class="form-field-label">状态</label>
            <div class="status-switch">
              <span>停用</span>
              <button 
                :class="['switch-btn', form.status === 1 && 'active']"
                @click="form.status = form.status === 1 ? 0 : 1"
              >
                <span class="switch-thumb"></span>
              </button>
              <span>启用</span>
            </div>
          </div>
          <div class="form-field-group">
            <label class="form-field-label">权限配置</label>
            <div class="permission-tree">
              <div 
                v-for="menu in menus" 
                :key="menu.id"
                class="tree-node"
              >
                <div 
                  class="tree-node-header"
                  @click="toggleExpandNode(menu)"
                >
                  <button 
                    :class="['tree-checkbox', menuChecked(menu) && 'checked']"
                    @click.stop="toggleMenuNode(menu)"
                  >
                    <svg v-if="menuChecked(menu)" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <polyline points="20 6 9 17 4 12"/>
                    </svg>
                  </button>
                  <svg 
                    v-if="menu.children?.length"
                    :class="['tree-expand', menuExpanded(menu) && 'expanded']" 
                    viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                  >
                    <polyline points="6 9 12 15 18 9"/>
                  </svg>
                  <span class="tree-label">{{ menu.menuName }}</span>
                </div>
                <div v-if="menu.children?.length && menuExpanded(menu)" class="tree-children">
                  <div 
                    v-for="child in menu.children" 
                    :key="child.id"
                    class="tree-child"
                  >
                    <button 
                      :class="['tree-checkbox', menuChecked(child) && 'checked']"
                      @click="toggleMenuNode(child)"
                    >
                      <svg v-if="menuChecked(child)" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <polyline points="20 6 9 17 4 12"/>
                      </svg>
                    </button>
                    <span class="tree-label">{{ child.menuName }}</span>
                    <div v-if="child.children?.length" class="tree-grandchildren">
                      <div 
                        v-for="grandchild in child.children" 
                        :key="grandchild.id"
                        class="tree-grandchild"
                      >
                        <button 
                          :class="['tree-checkbox', menuChecked(grandchild) && 'checked']"
                          @click="toggleMenuNode(grandchild)"
                        >
                          <svg v-if="menuChecked(grandchild)" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                            <polyline points="20 6 9 17 4 12"/>
                          </svg>
                        </button>
                        <span class="tree-label">{{ grandchild.menuName }}</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
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
