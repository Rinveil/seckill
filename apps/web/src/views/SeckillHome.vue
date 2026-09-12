<template>
  <el-card v-loading="loading">
    <template #header>自测抢购 · 活动列表</template>
    <el-empty v-if="!item" description="暂无活动" />
    <div v-else>
      <h3>{{ item.title }}</h3>
      <p>
        <span class="price">¥{{ (item.priceFen / 100).toFixed(2) }}</span>
        <span class="origin">¥{{ (item.originPriceFen / 100).toFixed(2) }}</span>
      </p>
      <p class="muted">库存 {{ item.stock }}</p>
      <el-button type="danger" @click="$router.push(`/seckill/activity/${item.id}`)">进入会场</el-button>
    </div>
  </el-card>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getActivities } from '../api'

const loading = ref(false)
const item = ref(null)

onMounted(async () => {
  loading.value = true
  try {
    const res = await getActivities()
    if (res.code !== 0) {
      ElMessage.error(res.message || '加载失败')
      return
    }
    item.value = res.data?.[0] || null
  } finally {
    loading.value = false
  }
})
</script>
