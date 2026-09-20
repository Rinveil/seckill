<template>
  <div class="mall-detail-page">
    <header class="mall-header">
      <div class="brand">
        <span class="brand-name">石头商城</span>
        <span class="brand-sub">STONE MALL</span>
      </div>
      <div class="header-actions">
        <el-button link @click="$router.push('/mall')">返回商城</el-button>
        <template v-if="loggedIn">
          <el-button link type="primary" @click="$router.push('/my/orders')">我的订单</el-button>
          <el-button link @click="onLogout">退出</el-button>
        </template>
        <template v-else>
          <el-button link type="primary" @click="$router.push('/login')">登录</el-button>
        </template>
      </div>
    </header>
    <main class="detail-main" v-loading="loading">
      <el-empty v-if="error" :description="error">
        <el-button type="primary" @click="$router.push('/mall')">返回商城</el-button>
      </el-empty>
      <div v-else class="detail-wrap">
        <div class="detail-img"><img src="/product.svg" alt="商品" /></div>
        <div class="detail-info">
          <h2>{{ item?.title }}</h2>
          <div class="price-row">
            <span class="seckill-price">¥{{ (item?.priceFen / 100 || 0).toFixed(2) }}</span>
            <span class="origin-price">¥{{ (item?.originPriceFen / 100 || 0).toFixed(2) }}</span>
            <el-tag type="danger" effect="plain">{{ discount }}折</el-tag>
          </div>
          <div class="info-list">
            <div><span>状态</span><el-tag :type="statusType" size="small">{{ statusText }}</el-tag></div>
            <div><span>库存</span><strong>{{ item?.stock }} 件</strong></div>
            <div v-if="item?.soldCount != null"><span>已抢</span><strong>{{ item.soldCount }} 件</strong></div>
            <div><span>限购</span><strong>每人 {{ item?.limitPerUser || 1 }} 件</strong></div>
            <div><span>开始</span><strong>{{ formatTime(item?.startAt) }}</strong></div>
            <div><span>结束</span><strong>{{ formatTime(item?.endAt) }}</strong></div>
          </div>
          <div class="countdown" v-if="countdown">{{ countdownLabel }} {{ countdown }}</div>
          <el-button type="primary" size="large" class="grab-btn" :disabled="!canGrab" @click="onGrab">
            {{ canGrab ? '立即抢购' : grabLabel }}
          </el-button>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getMallDetail, isLoggedIn, clearAuth } from '../api'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const item = ref(null)
const error = ref('')
const now = ref(Date.now())
const loggedIn = computed(() => isLoggedIn())
let timer

const started = computed(() => item.value && now.value >= Date.parse(item.value.startAt))
const ended = computed(() => item.value?.endAt && now.value >= Date.parse(item.value.endAt))
const canGrab = computed(() => item.value?.status === 'OPEN' && started.value && !ended.value)
const statusText = computed(() => {
  if (!item.value) return '-'
  if (item.value.status === 'CLOSED' || ended.value) return '已结束'
  if (item.value.status === 'OPEN') return '开抢中'
  if (item.value.status === 'PREHEATED') return '即将开始'
  return '未开抢'
})
const statusType = computed(() => {
  if (!item.value) return ''
  if (item.value.status === 'CLOSED' || ended.value) return 'info'
  if (item.value.status === 'OPEN') return 'success'
  if (item.value.status === 'PREHEATED') return 'warning'
  return ''
})
const discount = computed(() => item.value?.originPriceFen ? Math.round((item.value.priceFen / item.value.originPriceFen) * 10) : '-')
const grabLabel = computed(() => {
  if (!item.value) return '加载中'
  if (item.value.status === 'CLOSED' || ended.value) return '活动已结束'
  if (item.value.status !== 'OPEN') return '活动未开抢'
  if (!started.value) return '等待开始'
  return '立即抢购'
})
const countdownLabel = computed(() => {
  if (!item.value || item.value.status === 'CLOSED' || ended.value) return ''
  if (item.value.status === 'OPEN') return '距结束'
  return '距开始'
})
const countdown = computed(() => {
  if (!item.value || item.value.status === 'CLOSED' || ended.value) return ''
  const toStart = Date.parse(item.value.startAt) - now.value
  if (toStart > 0 && item.value.status !== 'OPEN') return fmt(toStart)
  if (item.value.endAt) {
    const toEnd = Date.parse(item.value.endAt) - now.value
    if (toEnd > 0) return fmt(toEnd)
  }
  return '活动进行中'
})
function fmt(ms) {
  const s = Math.floor(ms / 1000)
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  const sec = s % 60
  if (h > 0) return `${h}:${String(m).padStart(2,'0')}:${String(sec).padStart(2,'0')}`
  return `${m}:${String(sec).padStart(2,'0')}`
}
function formatTime(v) {
  if (!v) return '-'
  return new Date(v).toLocaleString('zh-CN', { hour12: false, timeZone: 'Asia/Shanghai' })
}
function onGrab() {
  if (!isLoggedIn()) { ElMessage.info('请先登录'); router.push({ path: '/login', query: { redirect: `/seckill/activity/${route.params.id}` } }); return }
  router.push(`/seckill/activity/${route.params.id}`)
}
function onLogout() { clearAuth(); router.go(0) }
async function load() {
  loading.value = true; error.value = ''
  try {
    const res = await getMallDetail(route.params.id)
    if (res.code !== 0) { error.value = res.message || '加载失败'; return }
    item.value = res.data
  } finally { loading.value = false }
}
onMounted(() => { load(); timer = setInterval(() => { now.value = Date.now() }, 500) })
onUnmounted(() => clearInterval(timer))
</script>

<style scoped>
.mall-detail-page { min-height: 100vh; background: #f5f6fa; }
.mall-header { display: flex; align-items: center; justify-content: space-between; padding: 14px 24px; background: #fff; border-bottom: 1px solid #eee; }
.brand-name { font-size: 20px; font-weight: 700; color: #ff5b5b; }
.brand-sub { margin-left: 10px; font-size: 13px; color: #999; }
.detail-main { max-width: 1000px; margin: 0 auto; padding: 20px; }
.detail-wrap { display: flex; gap: 32px; flex-wrap: wrap; background: #fff; border-radius: 12px; padding: 24px; }
.detail-img { flex: 0 0 320px; max-width: 320px; }
.detail-img img { width: 100%; border-radius: 8px; }
.detail-info { flex: 1; min-width: 280px; }
.detail-info h2 { margin: 0 0 16px; }
.price-row { display: flex; align-items: baseline; gap: 10px; margin-bottom: 20px; }
.seckill-price { font-size: 32px; font-weight: 700; color: #ff4d4f; }
.origin-price { font-size: 16px; color: #bbb; text-decoration: line-through; }
.info-list { margin-bottom: 20px; }
.info-list div { display: flex; justify-content: space-between; padding: 8px 0; border-bottom: 1px solid #f0f0f0; font-size: 14px; }
.info-list span { color: #909399; }
.countdown { font-size: 18px; font-weight: 600; color: #ff7a45; margin-bottom: 20px; }
.grab-btn { width: 200px; height: 48px; font-size: 18px; }
</style>
