<template>
  <div class="auth-page">
    <div class="auth-shell">
      <div class="auth-brand">
        <div class="mark">石</div>
        <h1>石头商城</h1>
        <p>运营与自测抢购工作台</p>
      </div>
      <el-card class="auth-card" shadow="never">
        <h2>登录账号</h2>
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent>
          <el-form-item label="用户名" prop="username">
            <el-input v-model="form.username" autocomplete="username" placeholder="请输入用户名" size="large" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              show-password
              autocomplete="current-password"
              placeholder="请输入密码"
              size="large"
              @keyup.enter="onSubmit"
            />
          </el-form-item>
          <el-button type="primary" size="large" :loading="loading" style="width: 100%" @click="onSubmit">
            进入石头商城
          </el-button>
        </el-form>
        <p class="auth-tip">
          演示管理员 admin / admin123<br />
          <router-link to="/register">注册普通用户</router-link>
        </p>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login, setAuth } from '../api'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const formRef = ref()
const form = reactive({ username: '', password: '' })

const rules = {
  username: [{ required: true, message: '用户名不能为空', trigger: 'blur' }],
  password: [{ required: true, message: '密码不能为空', trigger: 'blur' }]
}

async function onSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const res = await login({
      username: form.username.trim(),
      password: form.password
    })
    if (res.code !== 0) {
      ElMessage.error(res.message || '登录失败')
      return
    }
    setAuth(res.data)
    ElMessage.success('欢迎回来')
    router.replace(route.query.redirect || '/ops/activities')
  } finally {
    loading.value = false
  }
}
</script>
