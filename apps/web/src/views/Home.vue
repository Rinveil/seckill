<template>
  <div class="card" v-if="item">
    <h2>{{ item.title }}</h2>
    <p>
      <span class="price">¥{{ (item.priceFen / 100).toFixed(2) }}</span>
      <span class="origin">¥{{ (item.originPriceFen / 100).toFixed(2) }}</span>
    </p>
    <p class="muted">库存 {{ item.stock }}</p>
    <router-link :to="`/activity/${item.id}`"><button class="btn">进入会场</button></router-link>
  </div>
  <p v-else class="muted">加载活动中…</p>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { getActivities } from '../api'

const item = ref(null)

onMounted(async () => {
  const data = await getActivities()
  item.value = data.data?.[0]
})
</script>
