import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import Home from './views/Home.vue'
import Activity from './views/Activity.vue'
import Result from './views/Result.vue'
import './style.css'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: Home },
    { path: '/activity/:id', component: Activity },
    { path: '/result', component: Result }
  ]
})

createApp(App).use(router).mount('#app')
