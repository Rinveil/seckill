<template>
  <div class="card">
    <h2>{{ item?.title || '秒杀会场' }}</h2>
    <p class="muted">{{ countdown }}</p>
    <button class="btn" :disabled="!started || loading" @click="onGrab">
      {{ loading ? '抢购中…' : started ? '立即抢购' : '等待开始' }}
    </button>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getActivity, grab } from '../api'

const route = useRoute()
const router = useRouter()
const item = ref(null)
const now = ref(Date.now())
const loading = ref(false)
let timer

const started = computed(() => item.value && now.value >= Date.parse(item.value.startAt))
const countdown = computed(() => {
  if (!item.value) return ''
  const diff = Date.parse(item.value.startAt) - now.value
  if (diff <= 0) return '活动进行中'
  const s = Math.ceil(diff / 1000)
  return `距开始 ${s} 秒`
})

onMounted(async () => {
  const data = await getActivity(route.params.id)
  item.value = data.data
  timer = setInterval(() => { now.value = Date.now() }, 200)
})
onUnmounted(() => clearInterval(timer))

async function onGrab() {
  loading.value = true
  try {
    const data = await grab(route.params.id)
    router.push({ path: '/result', query: { ok: data.code === 0 ? '1' : '0', msg: data.message } })
  } finally {
    loading.value = false
  }
}
</script>
