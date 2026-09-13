<template>
  <el-card class="page-panel" shadow="never" v-loading="loading">
    <template #header>
      <div class="card-head">
        <div>
          <div class="title">活动管理</div>
          <div class="hint">DRAFT→预热→开抢→关闭(终态) · 关闭后须新建活动</div>
        </div>
        <el-button type="primary" @click="openCreate">新建活动</el-button>
      </div>
    </template>

    <el-table :data="rows" stripe empty-text="暂无活动">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="title" label="标题" min-width="140" />
      <el-table-column label="秒杀价" width="100">
        <template #default="{ row }">¥{{ (row.priceFen / 100).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column label="配置库存" prop="stock" width="90" />
      <el-table-column label="Redis库存" width="100">
        <template #default="{ row }">
          {{ row.redisStock == null ? '未预热' : row.redisStock }}
        </template>
      </el-table-column>
      <el-table-column label="开始" min-width="160">
        <template #default="{ row }">{{ formatLocal(row.startAt) }}</template>
      </el-table-column>
      <el-table-column label="结束" min-width="160">
        <template #default="{ row }">{{ formatLocal(row.endAt) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small" effect="plain">
            {{ statusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="420" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" :disabled="row.status === 'CLOSED'" @click="openEdit(row)">编辑</el-button>
          <el-button
            link
            type="warning"
            :disabled="row.status === 'OPEN' || row.status === 'CLOSED'"
            @click="onPreheat(row)"
          >预热</el-button>
          <el-button
            link
            type="warning"
            :disabled="row.status !== 'PREHEATED'"
            @click="openRedisStock(row)"
          >改Redis</el-button>
          <el-button
            v-if="row.status === 'OPEN'"
            link
            type="info"
            @click="onClose(row)"
          >关闭</el-button>
          <el-button
            v-else
            link
            type="success"
            :disabled="row.status !== 'PREHEATED'"
            @click="onOpen(row)"
          >开抢</el-button>
          <el-button
            link
            type="primary"
            @click="onReconcile(row)"
          >对账</el-button>
          <el-button link type="danger" :disabled="row.status === 'OPEN'" @click="onDelete(row)">
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>

  <el-dialog v-model="formVisible" :title="editingId ? '编辑活动' : '新建活动'" width="520px" destroy-on-close>
    <el-alert
      v-if="editingOpen"
      type="warning"
      :closable="false"
      show-icon
      title="开抢中仅可改标题；价格、库存、时间已锁定"
      style="margin-bottom: 14px"
    />
    <el-alert
      v-else-if="editingClosed"
      type="info"
      :closable="false"
      show-icon
      title="活动已结束（终态），不可修改，请新建活动"
      style="margin-bottom: 14px"
    />
    <el-form :model="form" label-width="108px">
      <el-form-item label="标题" required>
        <el-input v-model="form.title" maxlength="128" />
      </el-form-item>
      <el-form-item label="秒杀价(分)" required>
        <el-input-number v-model="form.priceFen" :min="1" :step="100" :disabled="editingOpen" />
      </el-form-item>
      <el-form-item label="原价(分)" required>
        <el-input-number v-model="form.originPriceFen" :min="1" :step="100" :disabled="editingOpen" />
      </el-form-item>
      <el-form-item label="配置库存" required>
        <el-input-number v-model="form.stock" :min="0" :disabled="editingOpen" />
        <div class="field-tip">仅写入 DB；预热后写入 Redis；关闭为终态不可再预热</div>
      </el-form-item>
      <el-form-item label="开始时间" required>
        <el-date-picker
          v-model="form.startAt"
          type="datetime"
          placeholder="本地时间"
          style="width: 100%"
          :disabled="editingOpen"
        />
      </el-form-item>
      <el-form-item label="结束时间" required>
        <el-date-picker
          v-model="form.endAt"
          type="datetime"
          placeholder="本地时间"
          style="width: 100%"
          :disabled="editingOpen"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="formVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" :disabled="editingClosed" @click="onSave">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="redisVisible" title="直接改 Redis 库存" width="400px" destroy-on-close>
    <el-form label-width="90px">
      <el-form-item label="Redis库存">
        <el-input-number v-model="redisStock" :min="0" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="redisVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="onSaveRedisStock">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  closeActivity,
  createActivity,
  deleteActivity,
  getActivities,
  openActivity,
  preheatActivity,
  reconcileActivity,
  updateActivity,
  updateRedisStock
} from '../api'

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const formVisible = ref(false)
const redisVisible = ref(false)
const editingId = ref(null)
const editingOpen = ref(false)
const editingClosed = ref(false)
const redisTargetId = ref(null)
const redisStock = ref(0)

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

const form = reactive({
  title: '',
  priceFen: 9900,
  originPriceFen: 39900,
  stock: 100,
  startAt: null,
  endAt: null
})

const timeFormatter = computed(() => new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hour12: false
}))

function formatLocal(value) {
  if (!value) return '-'
  const d = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(d.getTime())) return '-'
  return timeFormatter.value.format(d).replace(/\//g, '-')
}

function toIso(value) {
  if (!value) return null
  const d = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(d.getTime())) return null
  return d.toISOString()
}

function upsertRow(item) {
  if (!item?.id) return
  const idx = rows.value.findIndex((r) => r.id === item.id)
  if (idx >= 0) {
    rows.value[idx] = item
  } else {
    rows.value = [item, ...rows.value]
  }
}

async function load() {
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
}

function resetForm() {
  const now = Date.now()
  form.title = ''
  form.priceFen = 9900
  form.originPriceFen = 39900
  form.stock = 100
  form.startAt = new Date(now + 60_000)
  form.endAt = new Date(now + 3600_000)
}

function openCreate() {
  editingId.value = null
  editingOpen.value = false
  editingClosed.value = false
  resetForm()
  formVisible.value = true
}

function openEdit(row) {
  if (row.status === 'CLOSED') {
    ElMessage.warning('活动已结束（终态），请新建活动')
    return
  }
  editingId.value = row.id
  editingOpen.value = row.status === 'OPEN'
  editingClosed.value = false
  form.title = row.title
  form.priceFen = row.priceFen
  form.originPriceFen = row.originPriceFen
  form.stock = row.stock
  form.startAt = row.startAt ? new Date(row.startAt) : null
  form.endAt = row.endAt ? new Date(row.endAt) : null
  formVisible.value = true
}

function toPayload() {
  return {
    title: form.title.trim(),
    priceFen: form.priceFen,
    originPriceFen: form.originPriceFen,
    stock: form.stock,
    startAt: toIso(form.startAt),
    endAt: toIso(form.endAt)
  }
}

async function onSave() {
  const payload = toPayload()
  if (!payload.title || !payload.startAt || !payload.endAt) {
    ElMessage.warning('请填写完整（含有效的开始/结束时间）')
    return
  }
  if (new Date(payload.endAt) <= new Date(payload.startAt)) {
    ElMessage.warning('结束时间须晚于开始时间')
    return
  }
  saving.value = true
  try {
    const res = editingId.value
      ? await updateActivity(editingId.value, payload)
      : await createActivity(payload)
    if (res.code !== 0) {
      ElMessage.error(res.message || '保存失败')
      return
    }
    ElMessage.success('已保存')
    upsertRow(res.data)
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function onPreheat(row) {
  if (row.status === 'OPEN') {
    ElMessage.warning('开抢中禁止预热，避免覆盖现场库存')
    return
  }
  if (row.status === 'CLOSED') {
    ElMessage.warning('活动已结束（终态），请新建活动')
    return
  }
  const res = await preheatActivity(row.id)
  if (res.code !== 0) {
    ElMessage.error(res.message || '预热失败')
    return
  }
  ElMessage.success(`已预热 Redis 库存=${res.data.redisStock}`)
  upsertRow(res.data)
}

async function onOpen(row) {
  if (row.status !== 'PREHEATED') {
    ElMessage.warning('请先预热后再开抢')
    return
  }
  const res = await openActivity(row.id)
  if (res.code !== 0) {
    ElMessage.error(res.message || '开抢失败')
    return
  }
  ElMessage.success('已开抢')
  upsertRow(res.data)
}

async function onClose(row) {
  const res = await closeActivity(row.id)
  if (res.code !== 0) {
    ElMessage.error(res.message || '关闭失败')
    return
  }
  ElMessage.success('已关闭（终态，不可再开）')
  upsertRow(res.data)
}

async function onDelete(row) {
  await ElMessageBox.confirm(`确认删除活动「${row.title}」？`, '删除确认', { type: 'warning' })
  const res = await deleteActivity(row.id)
  if (res.code !== 0) {
    ElMessage.error(res.message || '删除失败')
    return
  }
  ElMessage.success('已删除')
  rows.value = rows.value.filter((r) => r.id !== row.id)
}

async function onReconcile(row) {
  const res = await reconcileActivity(row.id)
  if (res.code !== 0) {
    ElMessage.error(res.message || '对账失败')
    return
  }
  const d = res.data
  const text = [
    `活动 #${d.activityId} [${d.status}]`,
    `DB配置=${d.dbStock} Redis=${d.redisStock ?? '-'} init=${d.initStock ?? '-'}`,
    `订单 CREATED=${d.createdCount} PAID=${d.paidCount} CANCELLED=${d.cancelledCount} EXPIRED=${d.expiredCount}`,
    `占用(CREATED+PAID)=${d.occupied} 期望Redis=${d.expectedRedis ?? '-'}`,
    d.message
  ].join('\n')
  await ElMessageBox.alert(text, d.consistent ? '对账一致' : '对账不一致', {
    type: d.consistent ? 'success' : 'warning',
    confirmButtonText: '知道了'
  })
}

function openRedisStock(row) {
  if (row.status !== 'PREHEATED') {
    ElMessage.warning('仅已预热状态可改 Redis 库存')
    return
  }
  redisTargetId.value = row.id
  redisStock.value = row.redisStock ?? 0
  redisVisible.value = true
}

async function onSaveRedisStock() {
  saving.value = true
  try {
    const res = await updateRedisStock(redisTargetId.value, { stock: redisStock.value })
    if (res.code !== 0) {
      ElMessage.error(res.message || '修改失败')
      return
    }
    ElMessage.success('Redis 库存已更新')
    upsertRow(res.data)
    redisVisible.value = false
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.field-tip {
  margin-top: 4px;
  color: var(--muted, #6a767e);
  font-size: 12px;
  line-height: 1.4;
}
</style>
