// 学生路由只提供查证；工程模式显式构建，旧实验能力仍在工程路由中。
export const studentRoutes = [
  {path:'/',redirect:'/rules'},
  {path:'/rules',name:'RuleCheck',component:()=>import('../views/RuleCheckView.vue'),meta:{title:'校园规则查证 - 仲园', requiresRuleAuth:true}},
  {path:'/login',component:()=>import('../views/RuleAccount.vue'),meta:{title:'登录 - 仲园', accountMode:'login'}},
  {path:'/register',component:()=>import('../views/RuleAccount.vue'),meta:{title:'注册 - 仲园', accountMode:'register'}},
  {path:'/account',component:()=>import('../views/RuleAccount.vue'),meta:{title:'账号信息 - 仲园', accountMode:'account', requiresRuleAuth:true}},
  {path:'/:pathMatch(.*)*',redirect:{path:'/rules',query:{entry:'rules'}}}
]
