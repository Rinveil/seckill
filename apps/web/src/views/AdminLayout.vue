<template>
  <el-container class="admin-shell">
    <el-aside class="admin-aside">
      <div class="brand">
        <div class="brand-name">石头商城</div>
        <div class="brand-sub">STONE MALL</div>
      </div>
      <el-menu :default-active="active" router>
        <el-menu-item-group v-if="admin" title="运营区">
          <el-menu-item index="/ops/activities">活动管理</el-menu-item>
          <el-menu-item index="/ops/orders">订单管理</el-menu-item>
          <el-menu-item index="/ops/users">用户管理</el-menu-item>
        </el-menu-item-group>
        <el-menu-item-group title="自测抢购">
          <el-menu-item index="/seckill">活动会场</el-menu-item>
        </el-menu-item-group>
        <el-menu-item-group title="浏览">
          <el-menu-item index="/mall">商城首页</el-menu-item>
        </el-menu-item-group>
      </el-menu>
    </el-aside>
    <el-container class="admin-main-wrap">
      <el-header class="admin-header">
        <span class="user-chip">
          <strong>{{ user?.nickname || user?.username }}</strong>
          · {{ user?.role }}
        </span>
        <el-button link type="primary" @click="onLogout">退出</el-button>
      </el-header>
      <el-main class="admin-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { clearAuth, getUser, isAdmin } from '../api'

const route = useRoute()
const router = useRouter()
const user = computed(() => getUser())
const admin = computed(() => isAdmin())
const active = computed(() => {
  if (route.path.startsWith('/ops/orders')) return '/ops/orders'
  if (route.path.startsWith('/ops/users')) return '/ops/users'
  if (route.path.startsWith('/ops/activities')) return '/ops/activities'
  if (route.path.startsWith('/seckill')) return '/seckill'
  return route.path
})

function onLogout() {
  clearAuth()
  router.replace('/login')
}
</script>
