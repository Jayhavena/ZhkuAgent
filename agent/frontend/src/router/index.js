import { createRouter, createWebHistory } from 'vue-router'
import { studentRoutes } from './student-routes.js'
import { engineeringRoutes } from './engineering-routes.js'
import { controlledClient } from '../api/controlled.js'
import { controlledSession } from '../state/controlled-session.js'

// 入口模式在构建期定型：学生的运行期路由表只有查证页，工程模式才挂两个实验入口与回放。
const routes = import.meta.env.VITE_ENTRY_MODE === 'engineering' ? engineeringRoutes : studentRoutes

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 全局导航守卫，设置文档标题
router.beforeEach(async (to) => {
  // 设置页面标题
  if (to.meta.title) {
    document.title = to.meta.title
  }
  if (import.meta.env.VITE_ENTRY_MODE === 'engineering' && to.meta.requiresControlledAuth && !controlledSession.state.actor) {
    try { await controlledClient.me() }
    catch (error) {
      if (error.code === 'STALE_RESPONSE') return false
      return { path: '/campus/login', query: { return: to.fullPath } }
    }
  }
})

export default router

// 原Vite模板有body居中和#app宽度约束，仅在校园路由覆盖，保留旧演示样式。
router.afterEach((to, from, failure) => {
  if (failure) return
  document.body.classList.toggle('controlled-page', to.path.startsWith('/campus'))
  document.body.classList.toggle('rule-check-page', to.name === 'RuleCheck')
})
