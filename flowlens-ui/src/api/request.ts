import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'
import { useAuthStore } from '../stores/auth'

export interface ApiResult<T> {
  code: number
  message: string
  data: T
}

const service = axios.create({
  baseURL: '/api',
  timeout: 15000
})

const redirectToLogin = () => {
  useAuthStore().logout()
  if (router.currentRoute.value.path !== '/login') {
    router.replace('/login')
  }
}

service.interceptors.request.use((config) => {
  const token = localStorage.getItem('flowlens_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

service.interceptors.response.use(
    (response) => {
      const result = response.data as ApiResult<unknown>
      if (result.code && result.code !== 200) {
        ElMessage.error(result.message || '请求失败')
        if (result.code === 401) {
          redirectToLogin()
        }
        return Promise.reject(new Error(result.message))
      }
      return response.data
    },
    (error) => {
      const result = error.response?.data as Partial<ApiResult<unknown>> | undefined
      if (error.response?.status === 401 || result?.code === 401) {
        ElMessage.error(result?.message || '未登录或登录已过期')
        redirectToLogin()
        return Promise.reject(error)
      }
      ElMessage.error(result?.message || error.message || '网络异常')
      return Promise.reject(error)
    }
)

export default service