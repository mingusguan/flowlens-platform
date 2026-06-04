import request, { type ApiResult } from './request'

export interface LoginPayload {
  username: string
  password: string
}

export interface UserInfo {
  id: number
  username: string
  nickname: string
  email?: string
  mobile?: string
  status: number
  roles: string[]
  permissions: string[]
}

export interface LoginResult {
  token: string
  user: UserInfo
}

export interface MenuItem {
  id?: number
  parentId: number
  menuName: string
  menuType: string
  path?: string
  component?: string
  permission?: string
  icon?: string
  sortOrder: number
  visible: number
  children?: MenuItem[]
}

export interface UserItem {
  id?: number
  username: string
  password?: string
  nickname: string
  email?: string
  mobile?: string
  status: number
  roleIds: number[]
  createTime?: string
}

export interface RoleItem {
  id?: number
  roleName: string
  roleKey: string
  status: number
  menuIds: number[]
  createTime?: string
}

export interface DashboardOverview {
  cards: Array<{ label: string; value: number; trend: number }>
  trend: Array<{ day: string; play: number; like: number }>
}

export interface LiveAnchorItem {
  id?: number
  anchorName: string
  douyinLiveId?: string
  reportToken?: string
  status: number
  cloudCollectEnabled: number
  clientOnline?: number
  clientInstanceId?: string
  clientVersion?: string
  clientLastHeartbeatTime?: string
  cloudCollecting?: number
  createTime?: string
}

export interface LiveSessionItem {
  id: number
  anchorId: number
  anchorName?: string
  liveId?: string
  roomId?: string
  liveTitle?: string
  status: string
  activeSource: string
  cloudTaskId?: number
  startTime: string
  endTime?: string
}

export interface LiveGiftRankItem {
  rankKey?: string
  userId?: string
  douyinAccount?: string
  nickname: string
  giftValue: number
  giftCount: number
  giftEventCount: number
}

export interface LiveSummary {
  sessionId: number
  anchorId: number
  anchorName: string
  liveId?: string
  roomId?: string
  status: string
  startTime: string
  endTime?: string
  commentCount: number
  likeCount: number
  giftCount: number
  giftValue: number
  viewerCount: number
  giftRank: LiveGiftRankItem[]
}

export const loginApi = (data: LoginPayload) => request.post<unknown, ApiResult<LoginResult>>('/auth/login', data)
export const meApi = () => request.get<unknown, ApiResult<UserInfo>>('/auth/me')
export const menusApi = () => request.get<unknown, ApiResult<MenuItem[]>>('/auth/menus')
export const dashboardApi = () => request.get<unknown, ApiResult<DashboardOverview>>('/dashboard/overview')

export const listUsersApi = (keyword = '') => request.get<unknown, ApiResult<UserItem[]>>('/system/users', { params: { keyword } })
export const saveUserApi = (data: UserItem) => request.post<unknown, ApiResult<UserItem>>('/system/users', data)
export const deleteUserApi = (id: number) => request.delete<unknown, ApiResult<void>>(`/system/users/${id}`)
export const updateUserStatusApi = (id: number, status: number) => request.put<unknown, ApiResult<void>>(`/system/users/${id}/status`, { status })

export const listRolesApi = (keyword = '') => request.get<unknown, ApiResult<RoleItem[]>>('/system/roles', { params: { keyword } })
export const saveRoleApi = (data: RoleItem) => request.post<unknown, ApiResult<RoleItem>>('/system/roles', data)
export const deleteRoleApi = (id: number) => request.delete<unknown, ApiResult<void>>(`/system/roles/${id}`)

export const listMenusApi = () => request.get<unknown, ApiResult<MenuItem[]>>('/system/menus')
export const saveMenuApi = (data: MenuItem) => request.post<unknown, ApiResult<MenuItem>>('/system/menus', data)
export const deleteMenuApi = (id: number) => request.delete<unknown, ApiResult<void>>(`/system/menus/${id}`)

export const listLiveAnchorsApi = (keyword = '') => request.get<unknown, ApiResult<LiveAnchorItem[]>>('/live/anchors', { params: { keyword } })
export const saveLiveAnchorApi = (data: LiveAnchorItem) => request.post<unknown, ApiResult<LiveAnchorItem>>('/live/anchors', data)
export const deleteLiveAnchorApi = (id: number) => request.delete<unknown, ApiResult<void>>(`/live/anchors/${id}`)
export const startLiveSessionApi = (anchorId: number, data: { liveId?: string; roomId?: string; liveTitle?: string }) =>
  request.post<unknown, ApiResult<LiveSessionItem>>(`/live/anchors/${anchorId}/sessions`, data)
export const endLiveSessionApi = (sessionId: number) => request.post<unknown, ApiResult<void>>(`/live/sessions/${sessionId}/end`)
export const listLiveSessionsApi = (params: { anchorId?: number; status?: string } = {}) =>
  request.get<unknown, ApiResult<LiveSessionItem[]>>('/live/sessions', { params })
export const liveSummaryApi = (sessionId: number) => request.get<unknown, ApiResult<LiveSummary>>(`/live/sessions/${sessionId}/summary`)
