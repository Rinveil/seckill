<template>
  <el-card class="page-panel" shadow="never" v-loading="loading">
    <template #header>
      <div class="card-head">
        <div>
          <div class="title">用户管理</div>
          <div class="hint">管理员可创建账号、改角色/昵称、启停、重置密码 · 至少保留一名启用中的 ADMIN</div>
        </div>
        <el-button type="primary" @click="openCreate">新建用户</el-button>
      </div>
    </template>

    <div class="toolbar">
      <el-input
        v-model="query.keyword"
        clearable
        placeholder="用户名 / 昵称"
        style="width: 200px"
        @keyup.enter="reload"
      />
      <el-select v-model="query.role" clearable placeholder="角色" style="width: 120px" @change="reload">
        <el-option label="ADMIN" value="ADMIN" />
        <el-option label="USER" value="USER" />
      </el-select>
      <el-select v-model="query.status" clearable placeholder="状态" style="width: 120px" @change="reload">
        <el-option label="启用" value="ENABLED" />
        <el-option label="禁用" value="DISABLED" />
      </el-select>
      <el-button type="primary" @click="reload">查询</el-button>
    </div>

    <el-table :data="rows" stripe empty-text="暂无用户">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="username" label="用户名" min-width="120" />
      <el-table-column prop="nickname" label="昵称" min-width="120" />
      <el-table-column label="角色" width="100">
        <template #default="{ row }">
          <el-tag :type="row.role === 'ADMIN' ? 'danger' : 'info'" size="small" effect="plain">
            {{ row.role }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" size="small" effect="plain">
            {{ row.status === 'ENABLED' ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" min-width="170">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" min-width="280" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="warning" @click="openResetPwd(row)">重置密码</el-button>
          <el-tooltip :content="statusTip(row)" :disabled="!statusTip(row)">
            <span class="act">
              <el-button
                link
                :type="row.status === 'ENABLED' ? 'danger' : 'success'"
                :disabled="isSelf(row)"
                @click="onToggleStatus(row)"
              >
                {{ row.status === 'ENABLED' ? '禁用' : '启用' }}
              </el-button>
            </span>
          </el-tooltip>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        background
        layout="total, prev, pager, next"
        :total="total"
        :page-size="query.size"
        :current-page="query.page"
        @current-change="onPage"
      />
    </div>
  </el-card>

  <el-dialog v-model="createVisible" title="新建用户" width="460px" destroy-on-close>
    <el-form ref="createRef" :model="createForm" :rules="createRules" label-width="88px">
      <el-form-item label="用户名" prop="username">
        <el-input v-model="createForm.username" maxlength="32" placeholder="3~32 位" />
      </el-form-item>
      <el-form-item label="密码" prop="password">
        <el-input v-model="createForm.password" type="password" show-password maxlength="64" placeholder="6~64 位" />
      </el-form-item>
      <el-form-item label="昵称" prop="nickname">
        <el-input v-model="createForm.nickname" maxlength="64" placeholder="可选" />
      </el-form-item>
      <el-form-item label="角色" prop="role">
        <el-radio-group v-model="createForm.role">
          <el-radio value="USER">USER</el-radio>
          <el-radio value="ADMIN">ADMIN</el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="createVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="onCreate">创建</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="editVisible" title="编辑用户" width="460px" destroy-on-close>
    <el-form ref="editRef" :model="editForm" :rules="editRules" label-width="88px">
      <el-form-item label="用户名">
        <el-input :model-value="editForm.username" disabled />
      </el-form-item>
      <el-form-item label="昵称" prop="nickname">
        <el-input v-model="editForm.nickname" maxlength="64" />
      </el-form-item>
      <el-form-item label="角色" prop="role">
        <el-radio-group v-model="editForm.role" :disabled="isSelf({ id: editForm.id })">
          <el-radio value="USER">USER</el-radio>
          <el-radio value="ADMIN">ADMIN</el-radio>
        </el-radio-group>
        <div v-if="isSelf({ id: editForm.id })" class="field-tip">不能将自己降为普通用户</div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="editVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="onEdit">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="pwdVisible" title="重置密码" width="420px" destroy-on-close>
    <el-form ref="pwdRef" :model="pwdForm" :rules="pwdRules" label-width="88px">
      <el-form-item label="用户">
        <el-input :model-value="pwdForm.username" disabled />
      </el-form-item>
      <el-form-item label="新密码" prop="password">
        <el-input v-model="pwdForm.password" type="password" show-password maxlength="64" placeholder="6~64 位" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="pwdVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="onResetPwd">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createUser,
  getUser,
  getUsers,
  resetUserPassword,
  updateUser,
  updateUserStatus
} from '../api'

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const total = ref(0)
const me = getUser()

const query = reactive({
  keyword: '',
  role: '',
  status: '',
  page: 1,
  size: 20
})

const createVisible = ref(false)
const editVisible = ref(false)
const pwdVisible = ref(false)
const createRef = ref()
const editRef = ref()
const pwdRef = ref()

const createForm = reactive({
  username: '',
  password: '',
  nickname: '',
  role: 'USER'
})
const editForm = reactive({
  id: null,
  username: '',
  nickname: '',
  role: 'USER'
})
const pwdForm = reactive({
  id: null,
  username: '',
  password: ''
})

const createRules = {
  username: [
    { required: true, message: '用户名不能为空', trigger: 'blur' },
    { min: 3, max: 32, message: '用户名长度须为 3~32 位', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '密码不能为空', trigger: 'blur' },
    { min: 6, max: 64, message: '密码长度须为 6~64 位', trigger: 'blur' }
  ],
  nickname: [{ max: 64, message: '昵称最长 64 位', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }]
}
const editRules = {
  nickname: [{ max: 64, message: '昵称最长 64 位', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }]
}
const pwdRules = {
  password: [
    { required: true, message: '新密码不能为空', trigger: 'blur' },
    { min: 6, max: 64, message: '密码长度须为 6~64 位', trigger: 'blur' }
  ]
}

function isSelf(row) {
  return me?.userId != null && Number(me.userId) === Number(row.id)
}

function statusTip(row) {
  return isSelf(row) ? '不能禁用自己的账号' : ''
}

function formatTime(value) {
  if (!value) return '-'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return String(value)
  return d.toLocaleString('zh-CN', { hour12: false, timeZone: 'Asia/Shanghai' })
}

async function load() {
  loading.value = true
  try {
    const res = await getUsers({
      keyword: query.keyword.trim() || undefined,
      role: query.role || undefined,
      status: query.status || undefined,
      page: query.page,
      size: query.size
    })
    if (res.code !== 0) {
      ElMessage.error(res.message || '加载失败')
      return
    }
    rows.value = res.data?.list || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

function reload() {
  query.page = 1
  load()
}

function onPage(p) {
  query.page = p
  load()
}

function openCreate() {
  createForm.username = ''
  createForm.password = ''
  createForm.nickname = ''
  createForm.role = 'USER'
  createVisible.value = true
}

function openEdit(row) {
  editForm.id = row.id
  editForm.username = row.username
  editForm.nickname = row.nickname || ''
  editForm.role = row.role
  editVisible.value = true
}

function openResetPwd(row) {
  pwdForm.id = row.id
  pwdForm.username = row.username
  pwdForm.password = ''
  pwdVisible.value = true
}

async function onCreate() {
  const valid = await createRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const res = await createUser({
      username: createForm.username.trim(),
      password: createForm.password,
      nickname: createForm.nickname.trim() || undefined,
      role: createForm.role
    })
    if (res.code !== 0) {
      ElMessage.error(res.message || '创建失败')
      return
    }
    ElMessage.success('已创建')
    createVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function onEdit() {
  const valid = await editRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const res = await updateUser(editForm.id, {
      nickname: editForm.nickname.trim() || editForm.username,
      role: editForm.role
    })
    if (res.code !== 0) {
      ElMessage.error(res.message || '保存失败')
      return
    }
    ElMessage.success('已保存')
    editVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function onResetPwd() {
  const valid = await pwdRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const res = await resetUserPassword(pwdForm.id, { password: pwdForm.password })
    if (res.code !== 0) {
      ElMessage.error(res.message || '重置失败')
      return
    }
    ElMessage.success('密码已重置')
    pwdVisible.value = false
  } finally {
    saving.value = false
  }
}

async function onToggleStatus(row) {
  if (isSelf(row)) {
    ElMessage.warning('不能禁用自己的账号')
    return
  }
  const next = row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  const action = next === 'DISABLED' ? '禁用' : '启用'
  await ElMessageBox.confirm(`确认${action}用户「${row.username}」？`, `${action}确认`, { type: 'warning' })
  const res = await updateUserStatus(row.id, { status: next })
  if (res.code !== 0) {
    ElMessage.error(res.message || `${action}失败`)
    return
  }
  ElMessage.success(`已${action}`)
  await load()
}

onMounted(load)
</script>

<style scoped>
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 14px;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.act {
  display: inline-block;
}
.field-tip {
  margin-top: 4px;
  color: var(--muted, #6a767e);
  font-size: 12px;
}
</style>
