<template>
  <div class="result-wrap">
    <el-card class="page-panel" shadow="never">
      <el-result
        :icon="ok ? 'success' : 'error'"
        :title="title"
        :sub-title="subTitle"
      >
        <template #extra>
          <template v-if="ok && orderToken">
            <div class="order-info">
              <div class="info-row"><span>订单号</span><strong>{{ orderToken }}</strong></div>
              <div class="info-row" v-if="amount"><span>金额</span><strong class="price">¥{{ (amount / 100).toFixed(2) }}</strong></div>
              <div class="info-row" v-if="expireAt"><span>支付截止</span><strong :class="{ urgent: urgent }">{{ formatTime(expireAt) }}</strong></div>
            </div>
            <div class="actions">
              <el-button type="primary" :loading="paying" :disabled="!canPay" @click="onPay">立即支付</el-button>
              <el-button @click="$router.push('/my/orders')">我的订单</el-button>
              <el-button @click="$router.push('/mall')">返回商城</el-button>
            </div>
          </template>
          <template v-else>
            <div class="actions">
              <el-button type="primary" @click="$router.push('/mall')">返回商城</el-button>
              <el-button v-if="isAdminVal" @click="$router.push('/ops/activities')">活动管理</el-button>
            </div>
          </template>
        </template>
      </el-result>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getOrder, payOrder, isAdmin } from '../api'

const route = useRoute()
const router = useRouter()
const ok = computed(() => route.query.ok === '1')
const msg = computed(() => route.query.msg || '')
const orderToken = computed(() => route.query.token || '')
const isAdminVal = computed(() => isAdmin())

const paying = ref(false)
const amount = ref(0)
const expireAt = ref('')
const status = ref('')
const now = ref(Date.now())
let timer

const title = computed(() => ok.value ? '抢购成功' : failTitle.value)
const failTitle = computed(() => {
  if (msg.value.includes('售罄') || msg.value.includes('SOLD')) return '手慢了，已售罄'
  if (msg.value.includes('重复') || msg.value.includes('DUPLICATE')) return '请勿重复下单'
  if (msg.value.includes('未开抢') || msg.value.includes('NOT_STARTED')) return '活动尚未开抢'
  if (msg.value.includes('繁忙')) return '系统繁忙，库存已回滚'
  return '抢购失败'
})
const subTitle = computed(() => {
  if (!ok.value) return msg.value || '请稍后重试'
  return '请在 3 分钟内完成支付，超时将自动关单并回滚库存'
})
const urgent = computed(() => {
  if (!expireAt.value) return false
  return Date.parse(expireAt.value) - now.value < 60000
})
const canPay = computed(() => status.value === 'CREATED')

async function loadOrder() {
  if (!orderToken.value) return
  // MQ 异步落单可能延迟，轮询几次
  for (let i = 0; i < 20; i++) {
    const res = await getOrder(orderToken.value)
    if (res.code === 0 && res.data) {
      amount.value = res.data.amountFen || 0
      expireAt.value = res.data.expireAt || ''
      status.value = res.data.status || ''
      return
    }
    await new Promise(r => setTimeout(r, 1000))
  }
}

async function onPay() {
  paying.value = true
  try {
    const res = await payOrder(orderToken.value)
    if (res.code !== 0) {
      ElMessage.error(res.message || '支付失败')
      return
    }
    ElMessage.success('支付成功')
    status.value = 'PAID'
    setTimeout(() => router.push('/my/orders'), 1000)
  } finally {
    paying.value = false
  }
}

function formatTime(v) {
  if (!v) return '-'
  return new Date(v).toLocaleString('zh-CN', { hour12: false, timeZone: 'Asia/Shanghai' })
}

onMounted(() => {
  loadOrder()
  timer = setInterval(() => { now.value = Date.now() }, 500)
})
onUnmounted(() => clearInterval(timer))
</script>

<style scoped>
.order-info {
  margin: 12px 0 20px;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 8px;
  text-align: left;
}
.info-row {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 14px;
}
.info-row span { color: #909399; }
.info-row strong { color: #303133; }
.price { color: #ff4d4f !important; font-size: 18px; }
.urgent { color: #ff4d4f !important; }
.actions { display: flex; gap: 8px; justify-content: center; flex-wrap: wrap; }
</style>
