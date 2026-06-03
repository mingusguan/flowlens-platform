<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteUserApi, listRolesApi, listUsersApi, saveUserApi, updateUserStatusApi, type RoleItem, type UserItem } from '../../api/system'
import { Plus, RefreshCw, Search, Trash2, Edit, UserRound } from 'lucide-vue-next'

const loading = ref(false)
const keyword = ref('')
const users = ref<UserItem[]>([])
const roles = ref<RoleItem[]>([])
const dialogVisible = ref(false)
const form = reactive<UserItem>({
  username: '',
  password: '',
  nickname: '',
  email: '',
  mobile: '',
  status: 1,
  roleIds: []
})

const normalizeUser = (user: UserItem): UserItem => ({
  ...user,
  roleIds: Array.isArray(user.roleIds) ? user.roleIds : []
})

const normalizeRole = (role: RoleItem): RoleItem => ({
  ...role,
  menuIds: Array.isArray(role.menuIds) ? role.menuIds : []
})

const loadData = async () => {
  loading.value = true
  try {
    const [userRes, roleRes] = await Promise.all([listUsersApi(keyword.value), listRolesApi()])
    users.value = (userRes.data || []).map(normalizeUser)
    roles.value = (roleRes.data || []).map(normalizeRole)
  } finally {
    loading.value = false
  }
}

const openDialog = (row?: UserItem) => {
  Object.assign(form, row ? { ...normalizeUser(row), password: '' } : {
    id: undefined,
    username: '',
    password: '123456',
    nickname: '',
    email: '',
    mobile: '',
    status: 1,
    roleIds: []
  })
  dialogVisible.value = true
}

const submit = async () => {
  await saveUserApi(form)
  ElMessage.success('保存成功')
  dialogVisible.value = false
  await loadData()
}

const remove = async (row: UserItem) => {
  await ElMessageBox.confirm(`确认删除用户 ${row.nickname}？`, '删除确认', { type: 'warning' })
  await deleteUserApi(row.id!)
  ElMessage.success('删除成功')
  await loadData()
}

const toggleRole = (roleId: number) => {
  if (!Array.isArray(form.roleIds)) {
    form.roleIds = []
  }
  const index = form.roleIds.indexOf(roleId)
  if (index > -1) {
    form.roleIds.splice(index, 1)
  } else {
    form.roleIds.push(roleId)
  }
}

const toggleStatus = async (row: UserItem) => {
  await updateUserStatusApi(row.id!, row.status)
  ElMessage.success('状态已更新')
}

const roleSelected = (role: RoleItem) => role.id !== undefined && form.roleIds.includes(role.id)
const toggleRoleNode = (role: RoleItem) => {
  if (role.id !== undefined) {
    toggleRole(role.id)
  }
}

onMounted(loadData)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">用户管理</h1>
      <p class="page-subtitle">管理系统用户账号和权限</p>
    </div>

    <div class="panel">
      <div class="panel-header">
        <div>
          <span class="panel-subtitle">System Users</span>
          <h3 class="panel-title">用户列表</h3>
        </div>
        <div class="panel-actions">
          <div class="search-input">
            <Search :size="16" />
            <input 
              v-model="keyword" 
              type="text" 
              placeholder="搜索用户名/昵称" 
              @keyup.enter="loadData"
            />
          </div>
          <button class="btn btn-secondary" @click="loadData">
            <RefreshCw :size="16" />
          </button>
          <button class="btn btn-primary" @click="openDialog()">
            <Plus :size="16" />
            新增用户
          </button>
        </div>
      </div>

      <div class="table-container">
        <table class="data-table">
          <thead>
            <tr>
              <th>用户信息</th>
              <th>邮箱</th>
              <th>手机</th>
              <th>角色</th>
              <th>状态</th>
              <th>创建时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in users" :key="row.id">
              <td>
                <div class="user-info-cell">
                  <div class="user-avatar-sm">
                    {{ row.nickname?.slice(0, 1) || 'A' }}
                  </div>
                  <div>
                    <strong class="user-name">{{ row.nickname }}</strong>
                    <span class="user-username">{{ row.username }}</span>
                  </div>
                </div>
              </td>
              <td>{{ row.email || '-' }}</td>
              <td>{{ row.mobile || '-' }}</td>
              <td>
                <div class="role-tags">
                  <span 
                    v-for="roleId in row.roleIds || []" 
                    :key="roleId"
                    class="role-tag"
                  >
                    {{ roles.find(r => r.id === roleId)?.roleName || '未知' }}
                  </span>
                </div>
              </td>
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
      <div class="modal" @click.stop>
        <div class="modal-header">
          <h3 class="modal-title">{{ form.id ? '编辑用户' : '新增用户' }}</h3>
          <button class="modal-close" @click="dialogVisible = false">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"/>
              <line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>
        <div class="modal-body">
          <div class="form-field-group">
            <label class="form-field-label">用户名</label>
            <input 
              v-model="form.username" 
              type="text" 
              class="form-field-control"
              :disabled="!!form.id"
              placeholder="请输入用户名"
            />
          </div>
          <div class="form-field-group">
            <label class="form-field-label">密码</label>
            <input 
              v-model="form.password" 
              type="password" 
              class="form-field-control"
              placeholder="编辑时留空表示不修改"
            />
          </div>
          <div class="form-field-group">
            <label class="form-field-label">昵称</label>
            <input 
              v-model="form.nickname" 
              type="text" 
              class="form-field-control"
              placeholder="请输入昵称"
            />
          </div>
          <div class="form-field-group">
            <label class="form-field-label">邮箱</label>
            <input 
              v-model="form.email" 
              type="email" 
              class="form-field-control"
              placeholder="请输入邮箱"
            />
          </div>
          <div class="form-field-group">
            <label class="form-field-label">手机</label>
            <input 
              v-model="form.mobile" 
              type="tel" 
              class="form-field-control"
              placeholder="请输入手机号"
            />
          </div>
          <div class="form-field-group">
            <label class="form-field-label">角色</label>
            <div class="form-field-control select-role">
              <div 
                v-for="role in roles" 
                :key="role.id"
                :class="['role-option', roleSelected(role) && 'selected']"
                @click="toggleRoleNode(role)"
              >
                <div class="role-checkbox">
                  <svg v-if="roleSelected(role)" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <polyline points="20 6 9 17 4 12"/>
                  </svg>
                </div>
                <span>{{ role.roleName }}</span>
              </div>
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
        </div>
        <div class="modal-footer">
          <button class="btn btn-secondary" @click="dialogVisible = false">取消</button>
          <button class="btn btn-primary" @click="submit">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>
