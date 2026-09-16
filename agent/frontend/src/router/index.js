import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/Home.vue'),
    meta: {
      title: '首页 - 仲园 Agent',
      description: '仲园 Agent 本机演示，提供任务助手入口，展示任务处理过程与结果。'
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
router.beforeEach((to, from, next) => {
  // 设置页面标题
  if (to.meta.title) {
    document.title = to.meta.title
  }
  next()
})

export default router
