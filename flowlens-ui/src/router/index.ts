import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import LoginView from '../views/LoginView.vue'
import DashboardView from '../views/DashboardView.vue'
import UsersView from '../views/system/UsersView.vue'
import RolesView from '../views/system/RolesView.vue'
import MenusView from '../views/system/MenusView.vue'
import LiveAnchorsView from '../views/live/LiveAnchorsView.vue'
import AppLayout from '../layout/AppLayout.vue'

const routes: RouteRecordRaw[] = [
  { path: '/login', component: LoginView },
  {
    path: '/',
    component: AppLayout,
    redirect: '/dashboard',
    children: [
      { path: 'live/anchors', component: LiveAnchorsView, meta: { title: '直播值班', eyebrow: 'Live Duty' } },
      { path: 'dashboard', component: DashboardView, meta: { title: '工作台' } },
      { path: 'system/users', component: UsersView, meta: { title: '用户管理' } },
      { path: 'system/roles', component: RolesView, meta: { title: '角色管理' } },
      { path: 'system/menus', component: MenusView, meta: { title: '权限管理' } }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (to.path === '/login') return true
  if (!auth.token) return '/login'
  if (!auth.user) {
    await auth.loadProfile()
    await auth.loadMenus()
  }
  return true
})

export default router
