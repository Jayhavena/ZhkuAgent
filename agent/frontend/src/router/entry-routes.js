import { studentRoutes } from './student-routes.js'
import { engineeringRoutes } from './engineering-routes.js'

// 回放后端只有同步查证接口，不能挂载会提交异步任务的学生页面。
const replayRoutes = [
  { path: '/', redirect: '/rules' },
  { path: '/rules', name: 'RuleCheck', component: () => import('../views/RuleCheckReplayView.vue'), meta: { title: '离线规则查证 - 仲园' } },
  { path: '/:pathMatch(.*)*', redirect: '/rules' }
]

export function selectEntryRoutes(mode) {
  if (mode === 'replay') return replayRoutes
  return mode === 'engineering' ? engineeringRoutes : studentRoutes
}
