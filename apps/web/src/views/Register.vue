<template>
  <div class="shop-auth">
    <header class="shop-auth-top">
      <router-link to="/mall" class="shop-auth-logo">石头商城</router-link>
      <span class="shop-auth-welcome">欢迎注册</span>
    </header>
    <main class="shop-auth-banner">
      <div class="shop-auth-inner">
        <div class="shop-auth-promo">
          <img src="/product.svg" alt="" />
          <div class="shop-auth-promo-copy">
            <div class="kicker">限时秒杀</div>
            <div class="title">注册账号 参与抢购</div>
          </div>
        </div>
        <div class="shop-auth-box">
          <div class="shop-auth-tab">账号注册</div>
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
                autocomplete="new-password"
                placeholder="设置密码"
                size="large"
                @keyup.enter="onSubmit"
              />
            </el-form-item>
            <el-form-item prop="nickname">
              <el-input
                v-model="form.nickname"
                placeholder="昵称（选填）"
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
              注册
            </el-button>
          </el-form>
          <div class="shop-auth-links">
            <router-link to="/login">已有账号，去登录</router-link>
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
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { register, setAuth } from '../api'

const router = useRouter()
const loading = ref(false)
const formRef = ref()
const form = reactive({ username: '', password: '', nickname: '' })

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 32, message: '用户名须为 3~32 位', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 64, message: '密码须为 6~64 位', trigger: 'blur' }
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
    router.replace('/mall')
  } finally {
    loading.value = false
  }
}
</script>
