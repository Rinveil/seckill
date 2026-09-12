<template>
  <div class="auth-page">
    <el-card class="auth-card">
      <h2>注册</h2>
      <el-form :model="form" @submit.prevent>
        <el-form-item label="用户名">
          <el-input v-model="form.username" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" placeholder="可选" />
        </el-form-item>
        <el-button type="primary" :loading="loading" style="width: 100%" @click="onSubmit">注册</el-button>
      </el-form>
      <p class="auth-tip">注册固定为 USER · <router-link to="/login">去登录</router-link></p>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { register, setAuth } from '../api'

const router = useRouter()
const loading = ref(false)
const form = reactive({ username: '', password: '', nickname: '' })

async function onSubmit() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const res = await register({
      username: form.username,
      password: form.password,
      nickname: form.nickname || undefined
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
