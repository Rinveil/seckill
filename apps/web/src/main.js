import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import { ElMessage } from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import App from './App.vue'
import { isAdmin, isLoggedIn, setUnauthorizedHandler } from './api'
import './style.css'

import Login from './views/Login.vue'
import Register from './views/Register.vue'
import AdminLayout from './views/AdminLayout.vue'
import ActivityManage from './views/ActivityManage.vue'
import OrderManage from './views/OrderManage.vue'
import UserManage from './views/UserManage.vue'
import SeckillHome from './views/SeckillHome.vue'
import Activity from './views/Activity.vue'
import Result from './views/Result.vue'
import Mall from './views/Mall.vue'
import MallDetail from './views/MallDetail.vue'
import MyOrders from './views/MyOrders.vue'
import Dashboard from './views/Dashboard.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: Login, meta: { public: true } },
    { path: '/register', component: Register, meta: { public: true } },
    { path: '/mall', component: Mall, meta: { public: true } },
    { path: '/mall/:id', component: MallDetail, meta: { public: true } },
    {
      path: '/',
      component: AdminLayout,
      redirect: () => (isAdmin() ? '/ops/activities' : '/mall'),
      children: [
        { path: 'ops/activities', component: ActivityManage, meta: { admin: true } },
        { path: 'ops/orders', component: OrderManage, meta: { admin: true } },
        { path: 'ops/users', component: UserManage, meta: { admin: true } },
        { path: 'ops/dashboard', component: Dashboard, meta: { admin: true } },
        { path: 'my/orders', component: MyOrders },
        { path: 'seckill', component: SeckillHome },
        { path: 'seckill/activity/:id', component: Activity },
        { path: 'seckill/result', component: Result }
      ]
    }
  ]
})

router.beforeEach((to) => {
  if (to.meta.public) return true
  if (!isLoggedIn()) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.admin && !isAdmin()) {
    ElMessage.warning('需要管理员权限')
    return { path: '/seckill' }
  }
  return true
})

setUnauthorizedHandler((message) => {
  ElMessage.warning(message || '登录已过期，请重新登录')
  const redirect = router.currentRoute.value.fullPath
  router.replace({
    path: '/login',
    query: redirect.startsWith('/login') ? {} : { redirect }
  })
})

createApp(App).use(router).use(ElementPlus, { locale: zhCn }).mount('#app')
