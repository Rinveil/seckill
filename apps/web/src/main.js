import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import App from './App.vue'
import { isLoggedIn } from './api'
import './style.css'

import Login from './views/Login.vue'
import Register from './views/Register.vue'
import AdminLayout from './views/AdminLayout.vue'
import ActivityManage from './views/ActivityManage.vue'
import OrderManage from './views/OrderManage.vue'
import SeckillHome from './views/SeckillHome.vue'
import Activity from './views/Activity.vue'
import Result from './views/Result.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: Login, meta: { public: true } },
    { path: '/register', component: Register, meta: { public: true } },
    {
      path: '/',
      component: AdminLayout,
      redirect: '/ops/activities',
      children: [
        { path: 'ops/activities', component: ActivityManage },
        { path: 'ops/orders', component: OrderManage },
        { path: 'seckill', component: SeckillHome },
        { path: 'seckill/activity/:id', component: Activity },
        { path: 'seckill/result', component: Result }
      ]
    }
  ]
})

router.beforeEach((to) => {
  if (to.meta.public) return true
  if (!isLoggedIn()) return { path: '/login', query: { redirect: to.fullPath } }
  return true
})

createApp(App).use(router).use(ElementPlus, { locale: zhCn }).mount('#app')
