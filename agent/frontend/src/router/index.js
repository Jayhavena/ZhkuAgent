import { createRouter, createWebHistory } from 'vue-router'
import { controlledClient } from '../api/controlled.js'
import { controlledSession } from '../state/controlled-session.js'

const routes = [
  { path: '/rules', name: 'RuleCheck', component: () => import('../views/RuleCheckView.vue'), meta: { title: '校园规则查证 - 仲园' } },
  { path: '/campus/login', component: () => import('../views/ControlledLogin.vue'), meta: { title: '登录 - 仲园 Agent' } },
  { path: '/campus', component: () => import('../components/ControlledLayout.vue'), meta: { requiresControlledAuth: true }, children: [
    { path: '', redirect: '/campus/run' },
    { path: 'run', component: () => import('../views/CampusRun.vue'), meta: { title: '学分任务 - 仲园 Agent', campusTitle: '学分任务' } },
    { path: 'history', component: () => import('../views/CampusHistory.vue'), meta: { title: '我的历史 - 仲园 Agent', campusTitle: '我的历史' } },
    { path: 'conversation', component: () => import('../views/CampusConversation.vue'), meta: { title: '重修咨询 - 仲园 Agent', campusTitle: '重修咨询' } }
  ] },
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/Home.vue'),
    meta: {
      title: '首页 - 仲园 Agent',
      description: '校园规则查证：自然语言提问、必要条件确认和原文核对。'
    }
  },
  {
    path: '/super-agent',
    name: 'Agent',
    component: () => import('../views/Agent.vue'),
    meta: {
      title: '任务助手 - 仲园 Agent',
      description: '仲园 Agent 的任务助手演示页面，可提交任务并查看处理过程与结果。'
    }
  }
]

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
  if (to.meta.requiresControlledAuth && !controlledSession.state.actor) {
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
