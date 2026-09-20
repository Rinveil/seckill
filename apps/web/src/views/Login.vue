<template>
  <div class="shop-auth">
    <header class="shop-auth-top">
      <router-link to="/mall" class="shop-auth-logo">石头商城</router-link>
      <span class="shop-auth-welcome">欢迎登录</span>
    </header>
    <main class="shop-auth-banner">
      <div class="shop-auth-inner">
        <div class="shop-auth-promo">
          <img src="/product.svg" alt="" />
          <div class="shop-auth-promo-copy">
            <div class="kicker">限时秒杀</div>
            <div class="title">好货低价 准时开抢</div>
          </div>
        </div>
        <div class="shop-auth-box">
          <div class="shop-auth-tab">密码登录</div>
          <el-form
            ref="formRef"
            :model="form"
            :rules="rules"
            hide-required-asterisk
            @submit.prevent
          >
            <el-form-item prop="username">
              <el-input
                v-model="form.username"
                autocomplete="username"
                placeholder="用户名"
                size="large"
                @keyup.enter="onSubmit"
              />
            </el-form-item>
            <el-form-item prop="password">
              <el-input
                v-model="form.password"
                type="password"
                show-password
                autocomplete="current-password"
                placeholder="密码"
                size="large"
                @keyup.enter="onSubmit"
              />
            </el-form-item>
            <el-button
              class="shop-auth-submit"
              type="primary"
              size="large"
              :loading="loading"
              native-type="button"
              @click="onSubmit"
            >
              登录
            </el-button>
          </el-form>
          <div class="shop-auth-links">
            <router-link to="/register">免费注册</router-link>
            <router-link to="/mall">返回商城</router-link>
          </div>
        </div>
      </div>
    </main>
    <footer class="shop-auth-foot">石头商城</footer>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login, homePathForRole, setAuth } from '../api'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const formRef = ref()
const form = reactive({ username: '', password: '' })

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

function safeRedirect(role) {
  const fallback = homePathForRole(role)
  const raw = typeof route.query.redirect === 'string' ? route.query.redirect : ''
  if (!raw || raw.startsWith('/login')) return fallback
  if (raw.startsWith('/ops') && role !== 'ADMIN') return fallback
  return raw
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
    router.replace(safeRedirect(res.data.role))
  } finally {
    loading.value = false
  }
}
</script>
