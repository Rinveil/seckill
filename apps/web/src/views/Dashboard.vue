<template>
  <el-card class="page-panel" shadow="never" v-loading="loading">
    <template #header>
      <div class="card-head">
        <div><div class="title">数据看板</div><div class="hint">实时统计</div></div>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </template>
    <el-row :gutter="16">
      <el-col :xs="12" :sm="6"><el-card shadow="hover" class="stat-card"><div class="stat-val">{{ stats.activityCount }}</div><div class="stat-label">活动总数</div></el-card></el-col>
      <el-col :xs="12" :sm="6"><el-card shadow="hover" class="stat-card"><div class="stat-val open">{{ stats.openCount }}</div><div class="stat-label">开抢中</div></el-card></el-col>
      <el-col :xs="12" :sm="6"><el-card shadow="hover" class="stat-card"><div class="stat-val">{{ stats.orderCount }}</div><div class="stat-label">订单总数</div></el-card></el-col>
      <el-col :xs="12" :sm="6"><el-card shadow="hover" class="stat-card"><div class="stat-val paid">{{ stats.paidCount }}</div><div class="stat-label">已支付</div></el-card></el-col>
    </el-row>
    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :xs="12" :sm="8"><el-card shadow="hover" class="stat-card"><div class="stat-val gmv">¥{{ (stats.gmv / 100).toFixed(2) }}</div><div class="stat-label">成交总额</div></el-card></el-col>
      <el-col :xs="12" :sm="8"><el-card shadow="hover" class="stat-card"><div class="stat-val">{{ stats.pendingCount }}</div><div class="stat-label">待支付</div></el-card></el-col>
      <el-col :xs="12" :sm="8"><el-card shadow="hover" class="stat-card"><div class="stat-val danger">{{ stats.expiredCount }}</div><div class="stat-label">已超时</div></el-card></el-col>
    </el-row>
    <el-card style="margin-top: 16px" shadow="never">
      <template #header>活动抢购热度</template>
      <el-table :data="hotActivities" empty-text="暂无活动" stripe size="small">
        <el-table-column prop="title" label="活动" min-width="160" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column prop="stock" label="配置库存" width="90" />
        <el-table-column label="Redis库存" width="100">
          <template #default="{ row }">{{ row.redisStock == null ? '未预热' : row.redisStock }}</template>
        </el-table-column>
        <el-table-column label="已抢" width="80">
          <template #default="{ row }">{{ row.redisStock != null && row.stock != null ? row.stock - row.redisStock : '-' }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </el-card>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getActivities, getOrders } from '../api'

const loading = ref(false)
const activities = ref([])
const orders = ref([])

const stats = computed(() => {
  const s = { activityCount: 0, openCount: 0, orderCount: 0, paidCount: 0, gmv: 0, pendingCount: 0, expiredCount: 0 }
  s.activityCount = activities.value.length
  s.openCount = activities.value.filter(a => a.status === 'OPEN').length
  for (const o of orders.value) {
    s.orderCount++
    if (o.status === 'PAID') { s.paidCount++; s.gmv += o.amountFen || 0 }
    if (o.status === 'CREATED') s.pendingCount++
    if (o.status === 'EXPIRED') s.expiredCount++
  }
  return s
})
const hotActivities = computed(() =>
  activities.value.filter(a => a.redisStock != null).slice(0, 10)
)

async function load() {
  loading.value = true
  try {
    const [a, o] = await Promise.all([getActivities(), getOrders()])
    if (a.code === 0) activities.value = a.data || []
    if (o.code === 0) orders.value = o.data || []
  } finally { loading.value = false }
}
onMounted(load)
</script>

<style scoped>
.stat-card { text-align: center; padding: 8px 0; }
.stat-val { font-size: 28px; font-weight: 700; color: #303133; }
.stat-val.open { color: #67c23a; }
.stat-val.paid { color: #67c23a; }
.stat-val.gmv { color: #ff4d4f; }
.stat-val.danger { color: #f56c6c; }
.stat-label { font-size: 13px; color: #909399; margin-top: 4px; }
</style>
