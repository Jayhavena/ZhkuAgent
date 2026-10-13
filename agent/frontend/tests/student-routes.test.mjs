import test from 'node:test'
import assert from 'node:assert/strict'
import {createRouter,createMemoryHistory} from 'vue-router'
import {studentRoutes} from '../src/router/student-routes.js'
for(const path of ['/','/campus/run','/campus/history','/campus/conversation','/campus/login','/super-agent'])test(`student route ${path} resolves to rules only`,async()=>{const router=createRouter({history:createMemoryHistory(),routes:studentRoutes.map(r=>r.component?{...r,component:{render:()=>null}}:r)});await router.push(path);assert.equal(router.currentRoute.value.path,'/rules');assert.equal(router.currentRoute.value.name,'RuleCheck')})
