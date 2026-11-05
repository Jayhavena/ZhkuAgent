import { createRouter, createWebHistory } from 'vue-router'
import { replayRoutes } from './entry-routes.js'
import { studentRoutes } from './student-routes.js'
import { ruleCheckSession } from '../state/rule-check-session.js'
import { ruleTaskClient } from '../api/rule-task.js'
import { createRuleAuthGuard } from './rule-auth.js'

// 入口在构建期固定，离线演示须使用配套的同步回放页面。
// 静态分支让默认产物裁掉回放页面，不能只在运行时隐藏第二套界面。
const routes = import.meta.env.VITE_ENTRY_MODE === 'replay' ? replayRoutes : studentRoutes

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 离线回放与模拟演示不使用真实认证接口。
const ruleGuard = createRuleAuthGuard({ session: ruleCheckSession, client: ruleTaskClient })
if (import.meta.env.VITE_ENTRY_MODE !== 'replay' && !['simulated', 'http-fixture'].includes(import.meta.env.VITE_DEMO_MODE)) router.beforeEach(ruleGuard)

// 全局导航守卫，设置文档标题
router.beforeEach(async (to) => {
  // 设置页面标题
  if (to.meta.title) {
    document.title = to.meta.title
  }
})

export default router

// 查证页保留独立的全宽布局标记，账号页继续使用自己的布局。
router.afterEach((to, from, failure) => {
  if (failure) return
  document.body.classList.toggle('rule-check-page', to.name === 'RuleCheck')
})
