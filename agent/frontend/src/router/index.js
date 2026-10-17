import { createRouter, createWebHistory } from 'vue-router'
import { selectEntryRoutes } from './entry-routes.js'
import { controlledClient } from '../api/controlled.js'
import { controlledSession } from '../state/controlled-session.js'

// 入口在构建期固定，离线演示须使用配套的同步回放页面。
const routes = selectEntryRoutes(import.meta.env.VITE_ENTRY_MODE)

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
