<template>
  <el-card class="page-panel" shadow="never" v-loading="pageLoading">
    <el-empty v-if="loadError" :description="loadError">
      <el-button type="primary" @click="load">重试</el-button>
      <el-button link type="primary" @click="$router.push('/seckill')">返回会场列表</el-button>
    </el-empty>
    <div v-else class="arena-hero">
      <h3>{{ item?.title || '秒杀会场' }}</h3>
      <div class="arena-meta">
        <span>状态：{{ statusText }}</span>
        <span>
          库存 {{ item?.redisStock == null ? `DB ${item?.stock ?? '-'}` : `Redis ${item.redisStock}` }}
        </span>
        <span>{{ countdown }}</span>
      </div>
      <el-tooltip :content="grabHint" :disabled="canGrab || !grabHint">
        <span class="grab-wrap">
          <el-button
            class="grab-btn"
            type="primary"
            :disabled="!canGrab || loading"
            :loading="loading"
            @click="onGrab"
          >
            {{ grabLabel }}
          </el-button>
        </span>
      </el-tooltip>
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
const pageLoading = ref(false)
const loadError = ref('')
let timer

const started = computed(() => item.value && now.value >= Date.parse(item.value.startAt))
const ended = computed(() => item.value?.endAt && now.value >= Date.parse(item.value.endAt))
const canGrab = computed(() =>
  item.value?.status === 'OPEN' && started.value && !ended.value
)
const statusText = computed(() => {
  if (!item.value) return '-'
  if (item.value.status === 'CLOSED' || ended.value) return '已结束'
  if (item.value.status === 'OPEN') return '开抢中'
  if (item.value.status === 'PREHEATED') return '已预热'
  return '未开抢'
})
const grabLabel = computed(() => {
  if (!item.value) return '加载中'
  if (item.value.status === 'CLOSED' || ended.value) return '活动已结束'
  if (item.value.status !== 'OPEN') return '活动未开抢'
  if (!started.value) return '等待开始'
  return '立即抢购'
})
const grabHint = computed(() => {
  if (!item.value) return ''
  if (item.value.status === 'CLOSED' || ended.value) return '活动已结束，无法抢购'
  if (item.value.status !== 'OPEN') return '活动未开抢'
  if (!started.value) return '尚未到开始时间'
  return ''
})
const countdown = computed(() => {
  if (!item.value) return ''
  if (item.value.status === 'CLOSED' || ended.value) return '活动已结束'
  const toStart = Date.parse(item.value.startAt) - now.value
  if (toStart > 0) return `距开始 ${Math.ceil(toStart / 1000)} 秒`
  if (item.value.endAt) {
    const toEnd = Date.parse(item.value.endAt) - now.value
    if (toEnd > 0) return `距结束 ${Math.ceil(toEnd / 1000)} 秒`
  }
  return '活动进行中'
})

async function load() {
  pageLoading.value = true
  loadError.value = ''
  try {
    const res = await getActivity(route.params.id)
    if (res.code !== 0) {
      loadError.value = res.message || '加载失败'
      item.value = null
      return
    }
    item.value = res.data
  } finally {
    pageLoading.value = false
  }
}

onMounted(async () => {
  await load()
  timer = setInterval(() => { now.value = Date.now() }, 200)
})
onUnmounted(() => clearInterval(timer))

async function onGrab() {
  if (!canGrab.value) {
    ElMessage.warning(grabHint.value || '当前不可抢购')
    return
  }
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

<style scoped>
.grab-wrap {
  display: inline-block;
}
</style>
