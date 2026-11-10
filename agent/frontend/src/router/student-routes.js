// 学生入口只登记查证与账号页；旧链接统一引导到查证，避免暴露已退役页面。
export const studentRoutes = [
  {path:'/',redirect:'/rules'},
  {path:'/rules',name:'RuleCheck',component:()=>import('../views/RuleCheckView.vue'),meta:{title:'校园规则查证 - 仲园', requiresRuleAuth:true}},
  {path:'/login',component:()=>import('../views/RuleAccount.vue'),meta:{title:'登录 - 仲园', accountMode:'login'}},
  {path:'/register',component:()=>import('../views/RuleAccount.vue'),meta:{title:'注册 - 仲园', accountMode:'register'}},
  {path:'/account',component:()=>import('../views/RuleAccount.vue'),meta:{title:'账号信息 - 仲园', accountMode:'account', requiresRuleAuth:true}},
  {path:'/:pathMatch(.*)*',redirect:{path:'/rules',query:{entry:'rules'}}}
]
