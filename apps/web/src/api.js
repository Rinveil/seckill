const TOKEN_KEY = 'seckill_token'
const USER_KEY = 'seckill_user'

let unauthorizedHandler = null
let unauthorizedNotified = false

export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = handler
}

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function getUser() {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

export function setAuth(payload) {
  unauthorizedNotified = false
  if (payload?.token) {
    localStorage.setItem(TOKEN_KEY, payload.token)
  }
  localStorage.setItem(USER_KEY, JSON.stringify({
    userId: payload.userId,
    username: payload.username,
    role: payload.role,
    nickname: payload.nickname
  }))
}

export function clearAuth() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

export function isLoggedIn() {
  return Boolean(getToken())
}

export function isAdmin() {
  return getUser()?.role === 'ADMIN'
}

/** 登录后默认落地页 */
export function homePathForRole(role) {
  return role === 'ADMIN' ? '/ops/activities' : '/mall'
}

async function request(path, options = {}) {
  const headers = {
    'Content-Type': 'application/json',
    ...(options.headers || {})
  }
  const token = getToken()
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  let res
  try {
    res = await fetch(path, { ...options, headers })
  } catch {
    return { code: -1, message: '网络异常，请稍后重试', data: null }
  }
  const data = await res.json().catch(() => ({
    code: res.status || -1,
    message: '请求失败',
    data: null
  }))
  if (res.status === 401 || data.code === 401) {
    clearAuth()
    if (!unauthorizedNotified) {
      unauthorizedNotified = true
      unauthorizedHandler?.(data.message || '登录已过期，请重新登录')
    }
  }
  return data
}

export function login(body) {
  return request('/api/user/login', { method: 'POST', body: JSON.stringify(body) })
}

export function register(body) {
  return request('/api/user/register', { method: 'POST', body: JSON.stringify(body) })
}

export function me() {
  return request('/api/user/me')
}

export function getActivities() {
  return request('/api/activity/list')
}

export function getMallList() {
  return request('/api/mall/list')
}

export function getMallDetail(id) {
  return request(`/api/mall/${id}`)
}

export function getActivity(id) {
  return request(`/api/activity/${id}`)
}

export function createActivity(body) {
  return request('/api/activity', { method: 'POST', body: JSON.stringify(body) })
}

export function updateActivity(id, body) {
  return request(`/api/activity/${id}`, { method: 'PUT', body: JSON.stringify(body) })
}

export function deleteActivity(id) {
  return request(`/api/activity/${id}`, { method: 'DELETE' })
}

export function openActivity(id) {
  return request(`/api/activity/${id}/open`, { method: 'POST' })
}

export function closeActivity(id) {
  return request(`/api/activity/${id}/close`, { method: 'POST' })
}

export function preheatActivity(id) {
  return request(`/api/activity/${id}/preheat`, { method: 'POST' })
}

export function updateRedisStock(id, body) {
  return request(`/api/activity/${id}/redis-stock`, { method: 'PUT', body: JSON.stringify(body) })
}

export function reconcileActivity(id) {
  return request(`/api/activity/${id}/reconcile`)
}

export function grab(activityId) {
  return request(`/api/seckill/${activityId}`, { method: 'POST' })
}

export function getOrders() {
  return request('/api/order/list')
}

export function getOrder(orderNo) {
  return request(`/api/order/${orderNo}`)
}

export function payOrder(orderNo) {
  return request(`/api/order/${orderNo}/pay`, { method: 'POST' })
}

export function cancelOrder(orderNo) {
  return request(`/api/order/${orderNo}/cancel`, { method: 'POST' })
}

export function getUsers(params = {}) {
  const q = new URLSearchParams()
  if (params.keyword) q.set('keyword', params.keyword)
  if (params.role) q.set('role', params.role)
  if (params.status) q.set('status', params.status)
  q.set('page', String(params.page || 1))
  q.set('size', String(params.size || 20))
  return request(`/api/user/admin/list?${q.toString()}`)
}

export function createUser(body) {
  return request('/api/user/admin', { method: 'POST', body: JSON.stringify(body) })
}

export function updateUser(id, body) {
  return request(`/api/user/admin/${id}`, { method: 'PUT', body: JSON.stringify(body) })
}

export function updateUserStatus(id, body) {
  return request(`/api/user/admin/${id}/status`, { method: 'PUT', body: JSON.stringify(body) })
}

export function resetUserPassword(id, body) {
  return request(`/api/user/admin/${id}/password`, { method: 'PUT', body: JSON.stringify(body) })
}
