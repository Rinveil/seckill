<template>
  <div class="mall-page">
    <header class="mall-header">
      <div class="brand">
        <span class="brand-name">石头商城</span>
        <span class="brand-sub">STONE MALL · 限时秒杀</span>
      </div>
      <div class="header-actions">
        <template v-if="loggedIn">
          <el-button link type="primary" @click="$router.push(isAdmin ? '/ops/activities' : '/seckill')">进入控制台</el-button>
          <el-button link @click="onLogout">退出</el-button>
        </template>
        <template v-else>
          <el-button link type="primary" @click="$router.push('/login')">登录</el-button>
          <el-button link @click="$router.push('/register')">注册</el-button>
        </template>
      </div>
    </header>

    <main class="mall-main" v-loading="loading">
      <div class="filter-bar">
        <el-radio-group v-model="filter" size="small" @change="applyFilter">
          <el-radio-button label="">全部</el-radio-button>
          <el-radio-button label="OPEN">开抢中</el-radio-button>
          <el-radio-button label="PREHEATED">即将开始</el-radio-button>
          <el-radio-button label="CLOSED">已结束</el-radio-button>
        </el-radio-group>
        <el-button :loading="loading" size="small" @click="load">刷新</el-button>
      </div>

      <el-empty v-if="!filtered.length" description="暂无活动" />

      <el-row v-else :gutter="16">
        <el-col v-for="row in filtered" :key="row.id" :xs="24" :sm="12" :md="8" :lg="6">
          <el-card class="goods-card" shadow="hover" :body-style="{ padding: 0 }">
            <div class="goods-img">
              <img src="/product.svg" alt="秒杀商品" />
              <el-tag class="status-tag" :type="statusType(row.status)" effect="dark" size="small">
                {{ statusLabel(row.status) }}
              </el-tag>
            </div>
            <div class="goods-body">
              <div class="goods-title" :title="row.title">{{ row.title }}</div>
              <div class="price-row">
                <span class="seckill-price">¥{{ (row.priceFen / 100).toFixed(2) }}</span>
                <span class="origin-price">¥{{ (row.originPriceFen / 100).toFixed(2) }}</span>
                <el-tag size="small" type="danger" effect="plain" class="discount-tag">
                  {{ discount(row) }}折
                </el-tag>
              </div>
              <div class="countdown">{{ countdownText(row) }}</div>
              <el-button
                type="primary"
                class="enter-btn"
                :disabled="row.status === 'CLOSED'"
                @click="enter(row)"
              >
                {{ row.status === 'OPEN' ? '立即抢购' : row.status === 'PREHEATED' ? '查看详情' : '已结束' }}
              </el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getMallList, isAdmin, isLoggedIn, clearAuth } from '../api'

const router = useRouter()
const loading = ref(false)
const rows = ref([])
const filter = ref('')
const now = ref(Date.now())
const loggedIn = computed(() => isLoggedIn())
const isAdminVal = computed(() => isAdmin())
let timer

const filtered = computed(() =>
  filter.value ? rows.value.filter((r) => r.status === filter.value) : rows.value
)

function statusLabel(s) {
  if (s === 'OPEN') return '开抢中'
  if (s === 'PREHEATED') return '即将开始'
  if (s === 'CLOSED') return '已结束'
  return s
}
function statusType(s) {
  if (s === 'OPEN') return 'success'
  if (s === 'PREHEATED') return 'warning'
  if (s === 'CLOSED') return 'info'
  return ''
}
function discount(row) {
  if (!row.originPriceFen) return '-'
  return Math.round((row.priceFen / row.originPriceFen) * 10)
}
function countdownText(row) {
  if (row.status === 'CLOSED') return '活动已结束'
  if (row.status === 'OPEN') {
    const toEnd = Date.parse(row.endAt) - now.value
    return toEnd > 0 ? `距结束 ${fmt(toEnd)}` : '活动进行中'
  }
  if (row.status === 'PREHEATED') {
    const toStart = Date.parse(row.startAt) - now.value
    return toStart > 0 ? `距开始 ${fmt(toStart)}` : '即将开抢'
  }
  return ''
}
function fmt(ms) {
  const s = Math.ceil(ms / 1000)
  if (s < 60) return `${s}秒`
  const m = Math.floor(s / 60)
  if (m < 60) return `${m}分${s % 60}秒`
  const h = Math.floor(m / 60)
  return `${h}小时${m % 60}分`
}

function applyFilter() {}

function enter(row) {
  if (!isLoggedIn()) {
    ElMessage.info('请先登录后再抢购')
    router.push({ path: '/login', query: { redirect: `/seckill/activity/${row.id}` } })
    return
  }
  router.push(`/seckill/activity/${row.id}`)
}

function onLogout() {
  clearAuth()
  router.go(0)
}

async function load() {
  loading.value = true
  try {
    const res = await getMallList()
    if (res.code !== 0) {
      ElMessage.error(res.message || '加载失败')
      return
    }
    rows.value = res.data || []
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  load()
  timer = setInterval(() => { now.value = Date.now() }, 500)
})
onUnmounted(() => clearInterval(timer))
</script>

<style scoped>
.mall-page {
  min-height: 100vh;
  background: #f5f6fa;
}
.mall-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 24px;
  background: #fff;
  border-bottom: 1px solid #eee;
}
.brand-name {
  font-size: 20px;
  font-weight: 700;
  color: #ff5b5b;
}
.brand-sub {
  margin-left: 10px;
  font-size: 13px;
  color: #999;
}
.mall-main {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
}
.filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.goods-card {
  margin-bottom: 16px;
  border-radius: 10px;
  overflow: hidden;
}
.goods-img {
  position: relative;
  height: 180px;
  background: #fff5f5;
}
.goods-img img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.status-tag {
  position: absolute;
  top: 10px;
  left: 10px;
}
.goods-body {
  padding: 12px 14px 14px;
}
.goods-title {
  font-size: 15px;
  font-weight: 600;
  color: #333;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-bottom: 8px;
}
.price-row {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 6px;
}
.seckill-price {
  font-size: 20px;
  font-weight: 700;
  color: #ff4d4f;
}
.origin-price {
  font-size: 13px;
  color: #bbb;
  text-decoration: line-through;
}
.discount-tag {
  margin-left: auto;
}
.countdown {
  font-size: 12px;
  color: #ff7a45;
  margin-bottom: 10px;
  min-height: 18px;
}
.enter-btn {
  width: 100%;
}
</style>
