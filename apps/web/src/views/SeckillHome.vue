<template>
  <el-card v-loading="loading">
    <template #header>自测抢购 · 活动列表</template>
    <el-empty v-if="!rows.length" description="暂无活动，请先在运营区创建并开抢" />
    <el-table v-else :data="rows" stripe>
      <el-table-column prop="title" label="活动" min-width="180" />
      <el-table-column label="秒杀价" width="110">
        <template #default="{ row }">
          <span class="price">¥{{ (row.priceFen / 100).toFixed(2) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="库存" width="120">
        <template #default="{ row }">
          {{ row.redisStock == null ? `DB ${row.stock}` : `Redis ${row.redisStock}` }}
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 'OPEN' ? 'success' : 'info'" size="small">
            {{ row.status === 'OPEN' ? '开' : '关' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button type="danger" link @click="$router.push(`/seckill/activity/${row.id}`)">
            进入会场
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getActivities } from '../api'

const loading = ref(false)
const rows = ref([])

onMounted(async () => {
  loading.value = true
  try {
    const res = await getActivities()
    if (res.code !== 0) {
      ElMessage.error(res.message || '加载失败')
      return
    }
    rows.value = res.data || []
  } finally {
    loading.value = false
  }
})
</script>
