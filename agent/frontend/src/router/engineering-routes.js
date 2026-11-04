// 工程独立入口的完整路由表：学生模式的运行期路由不使用本表，两个实验入口的底层能力只在这里保留。
// 与 student-routes.js 成对存在，便于在 Node 侧直接核对路由定义，不必依赖 import.meta.env 分支。
export const engineeringRoutes = [
  { path: '/rules', name: 'RuleCheck', component: () => import('../views/RuleCheckView.vue'), meta: { title: '校园规则查证 - 仲园', requiresRuleAuth: true } },
  { path: '/login', component: () => import('../views/RuleAccount.vue'), meta: { title: '登录 - 仲园', accountMode: 'login' } },
  { path: '/register', component: () => import('../views/RuleAccount.vue'), meta: { title: '注册 - 仲园', accountMode: 'register' } },
  { path: '/account', component: () => import('../views/RuleAccount.vue'), meta: { title: '账号信息 - 仲园', accountMode: 'account', requiresRuleAuth: true } },
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
  },
  { path: '/engineering/rules-replay', component: () => import('../views/RuleCheckReplayView.vue'), meta: { title: '工程回放 - 仲园' } }
]
