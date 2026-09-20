<template>
  <el-card class="page-panel" shadow="never" v-loading="pageLoading">
    <el-empty v-if="loadError" :description="loadError">
      <el-button type="primary" @click="load">重试</el-button>
      <el-button link type="primary" @click="$router.push('/mall')">返回商城</el-button>
    </el-empty>
    <div v-else class="arena-wrap">
      <div class="arena-img">
        <img src="/product.svg" alt="秒杀商品" />
        <el-tag class="status-badge" :type="statusType" effect="dark" size="large">{{ statusText }}</el-tag>
      </div>
      <div class="arena-info">
        <h3>{{ item?.title || '秒杀会场' }}</h3>
        <div class="price-row">
          <span class="seckill-price">¥{{ (item?.priceFen / 100 || 0).toFixed(2) }}</span>
          <span class="origin-price">¥{{ (item?.originPriceFen / 100 || 0).toFixed(2) }}</span>
          <el-tag size="small" type="danger" effect="plain">{{ discount }}折</el-tag>
        </div>
        <div class="limit-hint">每人限购 {{ item?.limitPerUser || 1 }} 件</div>
        <div class="stock-bar" v-if="item?.redisStock != null && item?.stock != null">
          <div class="stock-label">
            <span>已抢 {{ soldCount }} 件 / 共 {{ item.stock }} 件</span>
            <span class="remain">剩余 {{ item.redisStock }}</span>
          </div>
          <el-progress :percentage="soldPercent" :color="'#ff4d4f'" :show-text="false" :stroke-width="14" />
        </div>
        <div class="countdown-row">
          <span class="countdown-label">{{ countdownLabel }}</span>
          <span class="countdown-value" :class="{ urgent: urgent }">{{ countdown }}</span>
        </div>
        <el-tooltip :content="grabHint" :disabled="canGrab || !grabHint">
          <span class="grab-wrap">
            <el-button class="grab-btn" type="primary" size="large"
              :disabled="!canGrab || loading" :loading="loading" @click="onGrab">
              {{ grabLabel }}
            </el-button>
          </span>
        </el-tooltip>
        <div style="margin-top: 16px">
          <el-button link type="primary" @click="$router.push('/mall')">返回商城</el-button>
        </div>
      </div>
    </div>
  </el-card>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getActivity, grab } from '../api'

const route = useRoute()
const router = useRouter()
const item = ref(null)
const now = ref(Date.now())
const loading = ref(false)
const pageLoading = ref(false)
const loadError = ref('')
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
const discount = computed(() => {
  if (!item.value?.originPriceFen) return '-'
  return Math.round((item.value.priceFen / item.value.originPriceFen) * 10)
})
const soldCount = computed(() => {
  if (!item.value?.stock || item.value?.redisStock == null) return 0
  return item.value.stock - item.value.redisStock
})
const soldPercent = computed(() => {
  if (!item.value?.stock) return 0
  return Math.min(100, Math.round((soldCount.value / item.value.stock) * 100))
})
const grabLabel = computed(() => {
  if (!item.value) return '加载中'
  if (item.value.status === 'CLOSED' || ended.value) return '活动已结束'
  if (item.value.status !== 'OPEN') return '活动未开抢'
  if (!started.value) return '等待开始'
  return '立即抢购'
})
const grabHint = computed(() => {
  if (!item.value) return ''
  if (item.value.status === 'CLOSED' || ended.value) return '活动已结束，无法抢购'
  if (item.value.status !== 'OPEN') return '活动未开抢'
  if (!started.value) return '尚未到开始时间'
  return ''
})
const countdownLabel = computed(() => {
  if (!item.value) return ''
  if (item.value.status === 'CLOSED' || ended.value) return ''
  if (item.value.status === 'OPEN') return '距结束'
  if (item.value.status === 'PREHEATED' && started.value) return '即将开抢'
  return '距开始'
})
const countdown = computed(() => {
  if (!item.value) return ''
  if (item.value.status === 'CLOSED' || ended.value) return '活动已结束'
  const toStart = Date.parse(item.value.startAt) - now.value
  if (toStart > 0 && item.value.status !== 'OPEN') return fmt(toStart)
  if (item.value.endAt) {
    const toEnd = Date.parse(item.value.endAt) - now.value
    if (toEnd > 0) return fmt(toEnd)
  }
  return '活动进行中'
})
const urgent = computed(() => {
  if (!item.value?.endAt || item.value.status !== 'OPEN') return false
  return Date.parse(item.value.endAt) - now.value < 60000
})
function fmt(ms) {
  const s = Math.floor(ms / 1000)
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  const sec = s % 60
  if (h > 0) return `${h}:${String(m).padStart(2,'0')}:${String(sec).padStart(2,'0')}`
  return `${m}:${String(sec).padStart(2,'0')}`
}

async function load() {
  pageLoading.value = true
  loadError.value = ''
  try {
    const res = await getActivity(route.params.id)
    if (res.code !== 0) { loadError.value = res.message || '加载失败'; item.value = null; return }
    item.value = res.data
  } finally { pageLoading.value = false }
}
onMounted(async () => { await load(); timer = setInterval(() => { now.value = Date.now() }, 500) })
onUnmounted(() => clearInterval(timer))

async function onGrab() {
  if (!canGrab.value) { ElMessage.warning(grabHint.value || '当前不可抢购'); return }
  loading.value = true
  try {
    const data = await grab(route.params.id)
    router.push({
      path: '/seckill/result',
      query: {
        ok: data.code === 0 ? '1' : '0',
        msg: data.message || '',
        token: data.data?.orderToken || data.data?.orderNo || ''
      }
    })
  } finally { loading.value = false }
}
</script>

<style scoped>
.arena-wrap { display: flex; gap: 24px; flex-wrap: wrap; }
.arena-img { position: relative; flex: 0 0 300px; max-width: 300px; }
.arena-img img { width: 100%; border-radius: 8px; }
.status-badge { position: absolute; top: 12px; left: 12px; }
.arena-info { flex: 1; min-width: 280px; }
.arena-info h3 { margin: 0 0 12px; font-size: 20px; }
.price-row { display: flex; align-items: baseline; gap: 10px; margin-bottom: 16px; }
.seckill-price { font-size: 28px; font-weight: 700; color: #ff4d4f; }
.origin-price { font-size: 15px; color: #bbb; text-decoration: line-through; }
.stock-bar { margin-bottom: 16px; }
.limit-hint { font-size: 13px; color: #909399; margin-bottom: 12px; }
.stock-label { display: flex; justify-content: space-between; font-size: 13px; color: #909399; margin-bottom: 6px; }
.remain { color: #ff4d4f; font-weight: 600; }
.countdown-row { display: flex; align-items: center; gap: 8px; margin-bottom: 20px; font-size: 15px; }
.countdown-label { color: #909399; }
.countdown-value { font-size: 20px; font-weight: 700; color: #ff7a45; }
.countdown-value.urgent { color: #ff4d4f; }
.grab-btn { width: 200px; height: 48px; font-size: 18px; }
.grab-wrap { display: inline-block; }
</style>
