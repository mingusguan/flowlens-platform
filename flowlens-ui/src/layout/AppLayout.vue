<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { 
  BarChart3, 
  Gauge, 
  KeyRound, 
  LogOut, 
  Menu, 
  Search, 
  Settings, 
  ShieldCheck, 
  Users,
  Bell,
  ChevronDown,
  Podcast,
  RadioTower
} from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const showUserMenu = ref(false)

const iconMap: Record<string, typeof Menu> = {
  Gauge,
  Settings,
  Users,
  ShieldCheck,
  KeyRound,
  BarChart3,
  Menu,
  Bell,
  Podcast,
  RadioTower
}

const visibleMenus = computed(() => auth.menus.filter((menu) => menu.visible === 1 && menu.menuType !== 'BUTTON'))

const resolveIcon = (name?: string) => {
  return iconMap[(name || 'Menu') as keyof typeof iconMap] || Menu
}

const goPath = (path?: string) => {
  if (path) router.push(path)
}

const logout = () => {
  auth.logout()
  router.replace('/login')
}

const toggleUserMenu = () => {
  showUserMenu.value = !showUserMenu.value
}
</script>

<template>
  <div class="layout-shell">
    <aside class="layout-sidebar">
      <div class="sidebar-header">
        <div class="sidebar-logo">
          <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M12 2L2 7l10 5 10-5-10-5z" fill="currentColor" opacity="0.8"/>
            <path d="M2 17l10 5 10-5" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
            <path d="M2 12l10 5 10-5" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
          </svg>
        </div>
        <div>
          <span class="sidebar-title">FlowLens</span>
          <span class="sidebar-subtitle">流量棱镜</span>
        </div>
      </div>

      <nav class="sidebar-nav">
        <template v-for="menu in visibleMenus" :key="menu.id">
          <button
            v-if="!menu.children?.length"
            class="nav-item"
            :class="{ active: route.path === menu.path }"
            @click="goPath(menu.path)"
          >
            <component :is="resolveIcon(menu.icon)" :size="18" />
            <span>{{ menu.menuName }}</span>
          </button>
          <div v-else class="nav-group">
            <div class="nav-group-title">
              <component :is="resolveIcon(menu.icon)" :size="16" />
              <span>{{ menu.menuName }}</span>
            </div>
            <button
              v-for="child in menu.children?.filter((item) => item.visible === 1 && item.menuType !== 'BUTTON')"
              :key="child.id"
              class="nav-item child"
              :class="{ active: route.path === child.path }"
              @click="goPath(child.path)"
            >
              <component :is="resolveIcon(child.icon)" :size="16" />
              <span>{{ child.menuName }}</span>
            </button>
          </div>
        </template>
      </nav>

      <div class="nav-divider"></div>
    </aside>

    <section class="layout-main">
      <header class="topbar">
        <div class="topbar-left">
          <div>
            <span class="topbar-eyebrow">{{ route.meta.eyebrow || 'Dashboard' }}</span>
            <h1 class="topbar-title">{{ route.meta.title || '工作台' }}</h1>
          </div>
        </div>
        <div class="topbar-right">
          <div class="topbar-search">
            <Search :size="16" />
            <input type="text" placeholder="搜索账号、视频、告警..." />
          </div>
          
          <button class="topbar-action badge">
            <Bell :size="18" />
          </button>
          
          <button class="topbar-action">
            <Settings :size="18" />
          </button>
          
          <div class="topbar-user" @click="toggleUserMenu">
            <div class="user-avatar">
              {{ auth.user?.nickname?.slice(0, 1) || 'A' }}
            </div>
            <div class="user-info">
              <span class="user-name">{{ auth.user?.nickname }}</span>
              <span class="user-role">{{ auth.roles.join(', ') }}</span>
            </div>
            <ChevronDown :size="14" />
          </div>
        </div>
        
        <div v-if="showUserMenu" class="user-dropdown" @click.self="showUserMenu = false">
          <button class="dropdown-item" @click="logout">
            <LogOut :size="16" />
            <span>退出登录</span>
          </button>
        </div>
      </header>
      
      <main class="content">
        <RouterView />
      </main>
    </section>
  </div>
</template>
