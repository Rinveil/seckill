<template>
  <div class="auth-page">
    <div class="auth-shell">
      <div class="auth-brand">
        <div class="mark">石</div>
        <h1>石头商城</h1>
        <p>开通自测账号，参与会场抢购</p>
      </div>
      <el-card class="auth-card" shadow="never">
        <h2>注册账号</h2>
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent>
          <el-form-item label="用户名" prop="username">
            <el-input v-model="form.username" autocomplete="username" placeholder="3~32 位" size="large" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              show-password
              autocomplete="new-password"
              placeholder="6~64 位"
              size="large"
            />
          </el-form-item>
          <el-form-item label="昵称" prop="nickname">
            <el-input v-model="form.nickname" placeholder="可选，最长 64 位" size="large" />
          </el-form-item>
          <el-button type="primary" size="large" :loading="loading" style="width: 100%" @click="onSubmit">
            注册并进入
          </el-button>
        </el-form>
        <p class="auth-tip">
          用户名 3~32 位，密码至少 6 位 · 角色固定 USER<br />
          <router-link to="/login">已有账号去登录</router-link>
        </p>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { register, setAuth } from '../api'

const router = useRouter()
const loading = ref(false)
const formRef = ref()
const form = reactive({ username: '', password: '', nickname: '' })

const rules = {
  username: [
    { required: true, message: '用户名不能为空', trigger: 'blur' },
    { min: 3, max: 32, message: '用户名长度须为 3~32 位', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '密码不能为空', trigger: 'blur' },
    { min: 6, max: 64, message: '密码长度须为 6~64 位', trigger: 'blur' }
  ],
  nickname: [
    { max: 64, message: '昵称最长 64 位', trigger: 'blur' }
  ]
}

async function onSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const res = await register({
      username: form.username.trim(),
      password: form.password,
      nickname: form.nickname.trim() || undefined
    })
    if (res.code !== 0) {
      ElMessage.error(res.message || '注册失败')
      return
    }
    setAuth(res.data)
    ElMessage.success('注册成功')
    router.replace('/seckill')
  } finally {
    loading.value = false
  }
}
</script>
