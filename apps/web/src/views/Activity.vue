<template>
  <el-card class="page-panel" shadow="never">
    <div class="arena-hero">
      <h3>{{ item?.title || '秒杀会场' }}</h3>
      <div class="arena-meta">
        <span>状态：{{ item?.status === 'OPEN' ? '开抢中' : '未开抢' }}</span>
        <span>
          库存 {{ item?.redisStock == null ? `DB ${item?.stock ?? '-'}` : `Redis ${item.redisStock}` }}
        </span>
        <span>{{ countdown }}</span>
      </div>
      <el-button
        class="grab-btn"
        type="primary"
        :disabled="!canGrab || loading"
        :loading="loading"
        @click="onGrab"
      >
        {{ grabLabel }}
      </el-button>
      <div style="margin-top: 16px">
        <el-button link type="primary" @click="$router.push('/seckill')">返回会场列表</el-button>
      </div>
    </div>
  </el-card>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getActivity, grab } from '../api'

const route = useRoute()
const router = useRouter()
const item = ref(null)
const now = ref(Date.now())
const loading = ref(false)
let timer

const started = computed(() => item.value && now.value >= Date.parse(item.value.startAt))
const canGrab = computed(() => item.value?.status === 'OPEN' && started.value)
const grabLabel = computed(() => {
  if (!item.value) return '加载中'
  if (item.value.status !== 'OPEN') return '活动未开抢'
  if (!started.value) return '等待开始'
  return '立即抢购'
})
const countdown = computed(() => {
  if (!item.value) return ''
  const diff = Date.parse(item.value.startAt) - now.value
  if (diff <= 0) return '活动进行中'
  return `距开始 ${Math.ceil(diff / 1000)} 秒`
})

onMounted(async () => {
  const res = await getActivity(route.params.id)
  if (res.code !== 0) {
    ElMessage.error(res.message || '加载失败')
    return
  }
  item.value = res.data
  timer = setInterval(() => { now.value = Date.now() }, 200)
})
onUnmounted(() => clearInterval(timer))

async function onGrab() {
  loading.value = true
  try {
    const data = await grab(route.params.id)
    router.push({
      path: '/seckill/result',
      query: { ok: data.code === 0 ? '1' : '0', msg: data.message || '' }
    })
  } finally {
    loading.value = false
  }
}
</script>
