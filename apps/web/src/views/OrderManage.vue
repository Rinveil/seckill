<template>
  <el-card class="page-panel" shadow="never">
    <template #header>
      <div class="card-head">
        <div>
          <div class="title">订单管理</div>
          <div class="hint">Mock 支付固定成功 · 待支付 3 分钟超时自动关单并回滚库存</div>
        </div>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </template>
    <el-table :data="rows" v-loading="loading" empty-text="暂无订单" stripe>
      <el-table-column prop="orderNo" label="订单号" min-width="200" />
      <el-table-column prop="activityId" label="活动 ID" width="100" />
      <el-table-column prop="userId" label="用户" width="90" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small" effect="plain">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="金额" width="110">
        <template #default="{ row }">¥{{ ((row.amountFen || 0) / 100).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column label="支付截止" min-width="170">
        <template #default="{ row }">{{ formatTime(row.expireAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button
            link
            type="success"
            :disabled="!canPayOrCancel(row)"
            @click="onPay(row)"
          >Mock支付</el-button>
          <el-button
            link
            type="danger"
            :disabled="!canPayOrCancel(row)"
            @click="onCancel(row)"
          >取消</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { cancelOrder, getOrders, payOrder } from '../api'

const loading = ref(false)
const rows = ref([])

function statusLabel(status) {
  if (status === 'PAID') return '已支付'
  if (status === 'CANCELLED') return '已取消'
  if (status === 'EXPIRED') return '已超时'
  return '待支付'
}

function statusType(status) {
  if (status === 'PAID') return 'success'
  if (status === 'CANCELLED') return 'info'
  if (status === 'EXPIRED') return 'danger'
  return 'warning'
}

function formatTime(value) {
  if (!value) return '-'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return String(value)
  return d.toLocaleString('zh-CN', { hour12: false, timeZone: 'Asia/Shanghai' })
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
    if (res.code !== 0) {
      ElMessage.error(res.message || '加载失败')
      return
    }
    rows.value = res.data || []
  } finally {
    loading.value = false
  }
}

async function onPay(row) {
  const res = await payOrder(row.orderNo)
  if (res.code !== 0) {
    ElMessage.error(res.message || '支付失败')
    await load()
    return
  }
  ElMessage.success('Mock 支付成功')
  await load()
}

async function onCancel(row) {
  await ElMessageBox.confirm(`确认取消订单 ${row.orderNo}？将回滚 Redis 库存`, '取消确认', {
    type: 'warning'
  })
  const res = await cancelOrder(row.orderNo)
  if (res.code !== 0) {
    ElMessage.error(res.message || '取消失败')
    await load()
    return
  }
  ElMessage.success('已取消并回滚库存')
  await load()
}

onMounted(load)
</script>
