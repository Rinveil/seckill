<template>
  <el-card class="page-panel" shadow="never">
    <template #header>
      <div class="card-head">
        <div>
          <div class="title">订单管理</div>
          <div class="hint">Mock 支付固定成功 · 待支付 3 分钟超时自动关单并回滚库存 · 时间上海时区</div>
        </div>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </template>
    <div style="margin-bottom: 12px; display: flex; gap: 12px; align-items: center;">
      <el-select v-model="statusFilter" placeholder="状态筛选" clearable size="small" style="width: 140px">
        <el-option label="待支付" value="CREATED" />
        <el-option label="已支付" value="PAID" />
        <el-option label="已取消" value="CANCELLED" />
        <el-option label="已超时" value="EXPIRED" />
      </el-select>
      <el-button :loading="loading" size="small" @click="load">刷新</el-button>
    </div>
    <el-table :data="pagedRows" v-loading="loading" empty-text="暂无订单" stripe>
      <el-table-column prop="orderNo" label="订单号" min-width="200" />
      <el-table-column label="商品" min-width="140">
        <template #default="{ row }">{{ row.activityTitle || `活动${row.activityId}` }}</template>
      </el-table-column>
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
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-tooltip :content="actionTip(row)" :disabled="canPayOrCancel(row)">
            <span class="act">
              <el-button
                link
                type="success"
                :disabled="!canPayOrCancel(row)"
                @click="onPay(row)"
              >Mock支付</el-button>
            </span>
          </el-tooltip>
          <el-tooltip :content="actionTip(row)" :disabled="canPayOrCancel(row)">
            <span class="act">
              <el-button
                link
                type="danger"
                :disabled="!canPayOrCancel(row)"
                @click="onCancel(row)"
              >取消</el-button>
            </span>
          </el-tooltip>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-if="filteredRows.length > pageSize"
      style="margin-top: 16px; justify-content: flex-end; display: flex"
      v-model:current-page="currentPage"
      :page-size="pageSize"
      :total="filteredRows.length"
      layout="prev, pager, next, total"
    />
  </el-card>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { cancelOrder, getOrders, payOrder } from '../api'

const loading = ref(false)
const rows = ref([])
const statusFilter = ref('')
const currentPage = ref(1)
const pageSize = 10

const filteredRows = computed(() =>
  statusFilter.value ? rows.value.filter(r => r.status === statusFilter.value) : rows.value
)
const pagedRows = computed(() =>
  filteredRows.value.slice((currentPage.value - 1) * pageSize, currentPage.value * pageSize)
)

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

function actionTip(row) {
  if (row.status === 'PAID') return '订单已支付'
  if (row.status === 'CANCELLED') return '订单已取消'
  if (row.status === 'EXPIRED') return '订单已超时'
  if (row.status === 'CREATED' && row.expireAt && new Date(row.expireAt).getTime() <= Date.now()) {
    return '已过支付截止时间（以服务器关单为准）'
  }
  return '仅待支付且未超时可操作'
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
  if (!canPayOrCancel(row)) {
    ElMessage.warning(actionTip(row))
    return
  }
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
  if (!canPayOrCancel(row)) {
    ElMessage.warning(actionTip(row))
    return
  }
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

<style scoped>
.act {
  display: inline-block;
}
</style>
