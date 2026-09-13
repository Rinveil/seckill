<template>
  <div class="result-wrap">
    <el-card class="page-panel" shadow="never">
      <el-result
        :icon="ok ? 'success' : 'error'"
        :title="ok ? '抢购成功' : '抢购失败'"
        :sub-title="msg || (ok ? '订单已进入队列，可在订单管理查看' : '')"
      >
        <template #extra>
          <el-button type="primary" @click="$router.push('/seckill')">返回会场</el-button>
          <el-button @click="$router.push('/ops/orders')">查看订单</el-button>
        </template>
      </el-result>
    </el-card>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const ok = computed(() => route.query.ok === '1')
const msg = computed(() => route.query.msg || '')
</script>
