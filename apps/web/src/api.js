const TOKEN_KEY = 'seckill_token'
const USER_KEY = 'seckill_user'

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

async function request(path, options = {}) {
  const headers = {
    'Content-Type': 'application/json',
    ...(options.headers || {})
  }
  const token = getToken()
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  const res = await fetch(path, { ...options, headers })
  const data = await res.json().catch(() => ({ code: res.status, message: '请求失败', data: null }))
  if (res.status === 401 || data.code === 401) {
    clearAuth()
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

export function grab(activityId) {
  return request(`/api/seckill/${activityId}`, { method: 'POST' })
}

export function getOrders() {
  return request('/api/order/list')
}
