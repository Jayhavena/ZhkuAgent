import { studentRoutes } from './student-routes.js'
import { createRuleAuthGuard } from './rule-auth.js'
import { ruleCheckSession } from '../state/rule-check-session.js'
import { ruleCheckClient } from '../api/rule-check.js'

// 回放后端只有同步查证接口，不能挂载会提交异步任务的学生页面。
export const replayRoutes = [
  { path: '/', redirect: '/rules' },
  { path: '/rules', name: 'RuleCheck', component: () => import('../views/RuleCheckReplayView.vue'), meta: { title: '离线规则查证 - 仲园', requiresRuleAuth: true }, beforeEnter: createRuleAuthGuard({ session: ruleCheckSession, client: ruleCheckClient }) },
  { path: '/login', component: () => import('../views/RuleAccount.vue'), meta: { title: '登录 - 仲园', accountMode: 'login' } },
  { path: '/register', component: () => import('../views/RuleAccount.vue'), meta: { title: '注册 - 仲园', accountMode: 'register' } },
  // 回放入口跳过全局守卫，受保护页面各自先从同源 Cookie 恢复身份。
  { path: '/account', component: () => import('../views/RuleAccount.vue'), meta: { title: '账号信息 - 仲园', accountMode: 'account', requiresRuleAuth: true }, beforeEnter: createRuleAuthGuard({ session: ruleCheckSession, client: ruleCheckClient }) },
  { path: '/:pathMatch(.*)*', redirect: '/rules' }
]

export function selectEntryRoutes(mode) {
  // 只有显式回放构建可启用同步页面；未知模式统一回到学生入口，避免恢复旧实验能力。
  return mode === 'replay' ? replayRoutes : studentRoutes
}
