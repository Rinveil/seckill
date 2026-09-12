<template>
  <div class="card">
    <h2>{{ ok ? '抢购成功' : '未能抢到' }}</h2>
    <p class="muted">{{ msg }}</p>
    <router-link to="/"><button class="btn">返回首页</button></router-link>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const ok = computed(() => route.query.ok === '1')
const msg = computed(() => route.query.msg || (ok.value ? '订单正在生成' : '手慢了'))
</script>
