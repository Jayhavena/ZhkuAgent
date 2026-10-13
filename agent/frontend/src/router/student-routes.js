// 学生路由只提供查证；工程模式显式构建，旧实验能力仍在工程路由中。
export const studentRoutes = [
  {path:'/',redirect:'/rules'},
  {path:'/rules',name:'RuleCheck',component:()=>import('../views/RuleCheckView.vue'),meta:{title:'校园规则查证 - 仲园'}},
  {path:'/:pathMatch(.*)*',redirect:{path:'/rules',query:{entry:'rules'}}}
]
