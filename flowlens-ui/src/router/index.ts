import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import LoginView from '../views/LoginView.vue'
import DashboardView from '../views/DashboardView.vue'
import UsersView from '../views/system/UsersView.vue'
import RolesView from '../views/system/RolesView.vue'
import MenusView from '../views/system/MenusView.vue'
import LiveAnchorsView from '../views/live/LiveAnchorsView.vue'
import LiveAnchorCompareView from '../views/live/LiveAnchorCompareView.vue'
import LiveAudienceView from '../views/live/LiveAudienceView.vue'
import LiveEmotionsView from '../views/live/LiveEmotionsView.vue'
import LiveReportsView from '../views/live/LiveReportsView.vue'
import LiveSegmentsView from '../views/live/LiveSegmentsView.vue'
import LiveSessionCompareView from '../views/live/LiveSessionCompareView.vue'
import LiveSessionsView from '../views/live/LiveSessionsView.vue'
import LiveStatsView from '../views/live/LiveStatsView.vue'
import LiveTimelineView from '../views/live/LiveTimelineView.vue'
import LiveTopicsView from '../views/live/LiveTopicsView.vue'
import MobileLiveInputView from '../views/live/MobileLiveInputView.vue'
import AppLayout from '../layout/AppLayout.vue'

const routes: RouteRecordRaw[] = [
  { path: '/login', component: LoginView },
  { path: '/mobile/live-input', component: MobileLiveInputView, meta: { title: '手机录入直播链接' } },
  {
    path: '/',
    component: AppLayout,
    redirect: '/dashboard',
    children: [
      { path: 'live/anchors', component: LiveAnchorsView, meta: { title: '主播监听', eyebrow: 'Live Monitor' } },
      { path: 'live/sessions', component: LiveSessionsView, meta: { title: '直播场次', eyebrow: 'Live Review' } },
      { path: 'live/stats', component: LiveStatsView, meta: { title: '场次统计', eyebrow: 'Live Review' } },
      { path: 'live/timeline', component: LiveTimelineView, meta: { title: '互动时间轴', eyebrow: 'Live Review' } },
      { path: 'live/topics', component: LiveTopicsView, meta: { title: '游戏话题', eyebrow: 'Live Review' } },
      { path: 'live/audience', component: LiveAudienceView, meta: { title: '活跃观众', eyebrow: 'Live Review' } },
      { path: 'live/emotions', component: LiveEmotionsView, meta: { title: '情绪节奏', eyebrow: 'Live Review' } },
      { path: 'live/segments', component: LiveSegmentsView, meta: { title: '冷场区间', eyebrow: 'Live Review' } },
      { path: 'live/reports', component: LiveReportsView, meta: { title: '复盘报告', eyebrow: 'Live Review' } },
      { path: 'live/session-compare', component: LiveSessionCompareView, meta: { title: '多场对比', eyebrow: 'Live Review' } },
      { path: 'live/anchor-compare', component: LiveAnchorCompareView, meta: { title: '主播对比', eyebrow: 'Live Review' } },
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
  if (!auth.token) {
    return {
      path: '/login',
      query: { redirect: to.fullPath }
    }
  }
  if (!auth.user) {
    await auth.loadProfile()
    await auth.loadMenus()
  }
  return true
})

export default router
