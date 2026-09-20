<template>
  <el-card class="page-panel" shadow="never">
    <template #header>
      <div class="card-head">
        <div>
          <div class="title">我的订单</div>
          <div class="hint">待支付 3 分钟超时自动关单 · 模拟支付固定成功</div>
        </div>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </template>
    <el-table :data="rows" v-loading="loading" empty-text="暂无订单" stripe>
      <el-table-column prop="orderNo" label="订单号" min-width="200" />
      <el-table-column label="商品" min-width="140">
        <template #default="{ row }">{{ row.activityTitle || `活动${row.activityId}` }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small" effect="plain">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="金额" width="100">
        <template #default="{ row }">¥{{ ((row.amountFen || 0) / 100).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column label="支付截止" min-width="160">
        <template #default="{ row }">{{ formatTime(row.expireAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="success" :disabled="!canPayOrCancel(row)" @click="openPay(row)">支付</el-button>
          <el-button link type="danger" :disabled="!canPayOrCancel(row)" @click="onCancel(row)">取消</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>

  <el-dialog v-model="payVisible" title="模拟支付" width="400px" :close-on-click-modal="false">
    <div class="pay-dialog">
      <div class="pay-amount">¥{{ (payAmount / 100).toFixed(2) }}</div>
      <el-radio-group v-model="payMethod" style="margin-bottom: 16px">
        <el-radio-button label="wechat">微信支付</el-radio-button>
        <el-radio-button label="alipay">支付宝</el-radio-button>
      </el-radio-group>
      <div class="qr-area">
        <div class="qr-placeholder">
          <div class="qr-icon">{{ payMethod === 'wechat' ? '💚' : '🔵' }}</div>
          <div class="qr-text">{{ payMethod === 'wechat' ? '微信扫码支付' : '支付宝扫码支付' }}</div>
          <div class="qr-fake">（模拟二维码）</div>
        </div>
      </div>
      <div class="pay-hint">扫码后点击下方「已完成支付」模拟回调</div>
    </div>
    <template #footer>
      <el-button @click="payVisible = false">取消</el-button>
      <el-button type="primary" :loading="paying" @click="onPay">已完成支付</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { cancelOrder, getOrders, payOrder } from '../api'

const loading = ref(false)
const rows = ref([])
const payVisible = ref(false)
const payAmount = ref(0)
const payMethod = ref('wechat')
const paying = ref(false)
const payingOrderNo = ref('')

function statusLabel(s) {
  if (s === 'PAID') return '已支付'
  if (s === 'CANCELLED') return '已取消'
  if (s === 'EXPIRED') return '已超时'
  return '待支付'
}
function statusType(s) {
  if (s === 'PAID') return 'success'
  if (s === 'CANCELLED') return 'info'
  if (s === 'EXPIRED') return 'danger'
  return 'warning'
}
function formatTime(v) {
  if (!v) return '-'
  return new Date(v).toLocaleString('zh-CN', { hour12: false, timeZone: 'Asia/Shanghai' })
}
function canPayOrCancel(row) {
  if (row.status !== 'CREATED') return false
  if (!row.expireAt) return true
  return new Date(row.expireAt).getTime() > Date.now()
}

async function load() {
  loading.value = true
  try {
    const res = await getOrders()
    if (res.code !== 0) { ElMessage.error(res.message || '加载失败'); return }
    rows.value = res.data || []
  } finally { loading.value = false }
}

function openPay(row) {
  payAmount.value = row.amountFen || 0
  payingOrderNo.value = row.orderNo
  payMethod.value = 'wechat'
  payVisible.value = true
}

async function onPay() {
  paying.value = true
  try {
    const res = await payOrder(payingOrderNo.value)
    if (res.code !== 0) { ElMessage.error(res.message || '支付失败'); return }
    ElMessage.success('支付成功')
    payVisible.value = false
    await load()
  } finally { paying.value = false }
}

async function onCancel(row) {
  await ElMessageBox.confirm(`确认取消订单 ${row.orderNo}？将回滚库存`, '取消确认', { type: 'warning' })
  const res = await cancelOrder(row.orderNo)
  if (res.code !== 0) { ElMessage.error(res.message || '取消失败'); await load(); return }
  ElMessage.success('已取消并回滚库存')
  await load()
}
onMounted(load)
</script>

<style scoped>
.pay-dialog { text-align: center; }
.pay-amount { font-size: 32px; font-weight: 700; color: #ff4d4f; margin-bottom: 20px; }
.qr-area { margin-bottom: 16px; }
.qr-placeholder {
  width: 200px; height: 200px; margin: 0 auto;
  border: 2px dashed #dcdfe6; border-radius: 8px;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  background: #f5f7fa;
}
.qr-icon { font-size: 48px; margin-bottom: 8px; }
.qr-text { font-size: 14px; color: #606266; }
.qr-fake { font-size: 12px; color: #c0c4cc; margin-top: 4px; }
.pay-hint { font-size: 13px; color: #909399; }
</style>
