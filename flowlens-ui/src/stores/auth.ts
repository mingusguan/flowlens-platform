import { defineStore } from 'pinia'
import { loginApi, meApi, menusApi, type LoginPayload, type MenuItem, type UserInfo } from '../api/system'

interface AuthState {
  token: string
  user: UserInfo | null
  menus: MenuItem[]
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    token: localStorage.getItem('flowlens_token') || '',
    user: null,
    menus: []
  }),
  getters: {
    permissions: (state) => state.user?.permissions || [],
    roles: (state) => state.user?.roles || []
  },
  actions: {
    async login(payload: LoginPayload) {
      const { data } = await loginApi(payload)
      this.token = data.token
      this.user = data.user
      localStorage.setItem('flowlens_token', data.token)
      await this.loadMenus()
    },
    async loadProfile() {
      if (!this.token) return
      const { data } = await meApi()
      this.user = data
    },
    async loadMenus() {
      const { data } = await menusApi()
      this.menus = data
    },
    logout() {
      this.token = ''
      this.user = null
      this.menus = []
      localStorage.removeItem('flowlens_token')
    }
  }
})
