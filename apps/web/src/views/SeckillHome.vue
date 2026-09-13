<template>
  <el-card class="page-panel" shadow="never" v-loading="loading">
    <template #header>
      <div class="card-head">
        <div>
          <div class="title">活动会场</div>
          <div class="hint">石头商城 · 自测抢购</div>
        </div>
      </div>
    </template>
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
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small" effect="plain">
            {{ statusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button type="primary" link @click="$router.push(`/seckill/activity/${row.id}`)">
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

function statusLabel(status) {
  if (status === 'OPEN') return '开抢中'
  if (status === 'PREHEATED') return '已预热'
  if (status === 'CLOSED') return '已结束'
  return '草稿'
}

function statusType(status) {
  if (status === 'OPEN') return 'success'
  if (status === 'PREHEATED') return 'warning'
  if (status === 'CLOSED') return 'info'
  return ''
}

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
