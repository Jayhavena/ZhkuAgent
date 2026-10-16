import test from 'node:test'
import assert from 'node:assert/strict'
import {createRouter,createMemoryHistory} from 'vue-router'
import {studentRoutes} from '../src/router/student-routes.js'
// 学生构建只登记查证一条路由；首页与两个实验入口都必须被引导回查证。
for(const path of ['/','/campus/run','/campus/history','/campus/conversation','/campus/login','/super-agent'])test(`student route ${path} resolves to rules only`,async()=>{const router=createRouter({history:createMemoryHistory(),routes:studentRoutes.map(r=>r.component?{...r,component:{render:()=>null}}:r)});await router.push(path);assert.equal(router.currentRoute.value.path,'/rules');assert.equal(router.currentRoute.value.name,'RuleCheck');
  // 直接访问实验入口要留下学生入口标记，说明是被显式引导回来的，而不是路径被悄悄改写。
  if(path!=='/')assert.equal(router.currentRoute.value.query.entry,'rules')})
