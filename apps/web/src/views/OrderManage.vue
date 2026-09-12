<template>
  <el-card>
    <template #header>
      <div class="card-head">
        <span>订单管理</span>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </template>
    <el-table :data="rows" v-loading="loading" empty-text="暂无订单（后续 MQ 建单后可见）">
      <el-table-column prop="orderNo" label="订单号" />
      <el-table-column prop="activityId" label="活动 ID" width="100" />
      <el-table-column prop="status" label="状态" width="120" />
      <el-table-column label="金额" width="120">
        <template #default="{ row }">¥{{ ((row.amountFen || 0) / 100).toFixed(2) }}</template>
      </el-table-column>
    </el-table>
    <p class="muted">Mock 支付 / 取消回滚将在第 7 步实现</p>
  </el-card>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getOrders } from '../api'

const loading = ref(false)
const rows = ref([])

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

onMounted(load)
</script>
