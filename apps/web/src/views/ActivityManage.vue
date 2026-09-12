<template>
  <el-card v-loading="loading">
    <template #header>
      <div class="card-head">
        <span>活动管理</span>
        <el-button type="primary" @click="openCreate">新建活动</el-button>
      </div>
    </template>

    <el-table :data="rows" stripe empty-text="暂无活动">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="title" label="标题" min-width="160" />
      <el-table-column label="秒杀价" width="100">
        <template #default="{ row }">¥{{ (row.priceFen / 100).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column label="DB库存" prop="stock" width="90" />
      <el-table-column label="Redis库存" width="100">
        <template #default="{ row }">
          {{ row.redisStock == null ? '未预热' : row.redisStock }}
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 'OPEN' ? 'success' : 'info'" size="small">
            {{ row.status === 'OPEN' ? '开' : '关' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="360" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="warning" @click="onPreheat(row)">预热</el-button>
          <el-button link type="warning" :disabled="row.redisStock == null" @click="openRedisStock(row)">
            改Redis
          </el-button>
          <el-button
            v-if="row.status !== 'OPEN'"
            link
            type="success"
            @click="onOpen(row)"
          >开抢</el-button>
          <el-button
            v-else
            link
            type="info"
            @click="onClose(row)"
          >关闭</el-button>
          <el-button link type="danger" :disabled="row.status === 'OPEN'" @click="onDelete(row)">
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>

  <el-dialog v-model="formVisible" :title="editingId ? '编辑活动' : '新建活动'" width="520px" destroy-on-close>
    <el-form :model="form" label-width="100px">
      <el-form-item label="标题" required>
        <el-input v-model="form.title" maxlength="128" />
      </el-form-item>
      <el-form-item label="秒杀价(分)" required>
        <el-input-number v-model="form.priceFen" :min="1" :step="100" />
      </el-form-item>
      <el-form-item label="原价(分)" required>
        <el-input-number v-model="form.originPriceFen" :min="1" :step="100" />
      </el-form-item>
      <el-form-item label="DB库存" required>
        <el-input-number v-model="form.stock" :min="0" />
      </el-form-item>
      <el-form-item label="开始时间" required>
        <el-date-picker
          v-model="form.startAt"
          type="datetime"
          value-format="x"
          placeholder="开始时间"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="结束时间" required>
        <el-date-picker
          v-model="form.endAt"
          type="datetime"
          value-format="x"
          placeholder="结束时间"
          style="width: 100%"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="formVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
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
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  closeActivity,
  createActivity,
  deleteActivity,
  getActivities,
  openActivity,
  preheatActivity,
  updateActivity,
  updateRedisStock
} from '../api'

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const formVisible = ref(false)
const redisVisible = ref(false)
const editingId = ref(null)
const redisTargetId = ref(null)
const redisStock = ref(0)

const form = reactive({
  title: '',
  priceFen: 9900,
  originPriceFen: 39900,
  stock: 100,
  startAt: '',
  endAt: ''
})

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
  form.startAt = String(now + 60_000)
  form.endAt = String(now + 3600_000)
}

function openCreate() {
  editingId.value = null
  resetForm()
  formVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  form.title = row.title
  form.priceFen = row.priceFen
  form.originPriceFen = row.originPriceFen
  form.stock = row.stock
  form.startAt = String(Date.parse(row.startAt))
  form.endAt = String(Date.parse(row.endAt))
  formVisible.value = true
}

function toPayload() {
  return {
    title: form.title.trim(),
    priceFen: form.priceFen,
    originPriceFen: form.originPriceFen,
    stock: form.stock,
    startAt: new Date(Number(form.startAt)).toISOString(),
    endAt: new Date(Number(form.endAt)).toISOString()
  }
}

async function onSave() {
  if (!form.title.trim() || !form.startAt || !form.endAt) {
    ElMessage.warning('请填写完整')
    return
  }
  saving.value = true
  try {
    const payload = toPayload()
    const res = editingId.value
      ? await updateActivity(editingId.value, payload)
      : await createActivity(payload)
    if (res.code !== 0) {
      ElMessage.error(res.message || '保存失败')
      return
    }
    ElMessage.success('已保存')
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function onPreheat(row) {
  const res = await preheatActivity(row.id)
  if (res.code !== 0) {
    ElMessage.error(res.message || '预热失败')
    return
  }
  ElMessage.success(`已预热 Redis 库存=${res.data.redisStock}`)
  await load()
}

async function onOpen(row) {
  const res = await openActivity(row.id)
  if (res.code !== 0) {
    ElMessage.error(res.message || '开抢失败')
    return
  }
  ElMessage.success('已开抢')
  await load()
}

async function onClose(row) {
  const res = await closeActivity(row.id)
  if (res.code !== 0) {
    ElMessage.error(res.message || '关闭失败')
    return
  }
  ElMessage.success('已关闭')
  await load()
}

async function onDelete(row) {
  await ElMessageBox.confirm(`确认删除活动「${row.title}」？`, '删除确认', { type: 'warning' })
  const res = await deleteActivity(row.id)
  if (res.code !== 0) {
    ElMessage.error(res.message || '删除失败')
    return
  }
  ElMessage.success('已删除')
  await load()
}

function openRedisStock(row) {
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
    redisVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>
