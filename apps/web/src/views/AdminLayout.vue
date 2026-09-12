<template>
  <el-container class="admin-shell">
    <el-aside width="220px" class="admin-aside">
      <div class="brand">秒杀 B 端</div>
      <el-menu :default-active="active" router>
        <el-menu-item-group title="运营区">
          <el-menu-item index="/ops/activities">活动管理</el-menu-item>
          <el-menu-item index="/ops/orders">订单管理</el-menu-item>
        </el-menu-item-group>
        <el-menu-item-group title="自测抢购">
          <el-menu-item index="/seckill">活动会场</el-menu-item>
        </el-menu-item-group>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="admin-header">
        <span>{{ user?.nickname || user?.username }}（{{ user?.role }}）</span>
        <el-button link type="primary" @click="onLogout">退出</el-button>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { clearAuth, getUser } from '../api'

const route = useRoute()
const router = useRouter()
const user = computed(() => getUser())
const active = computed(() => {
  if (route.path.startsWith('/ops/orders')) return '/ops/orders'
  if (route.path.startsWith('/ops/activities')) return '/ops/activities'
  if (route.path.startsWith('/seckill')) return '/seckill'
  return route.path
})

function onLogout() {
  clearAuth()
  router.replace('/login')
}
</script>
