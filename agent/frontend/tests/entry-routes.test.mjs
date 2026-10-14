import test from 'node:test'
import assert from 'node:assert/strict'
import {readFile} from 'node:fs/promises'
import {createRouter,createMemoryHistory} from 'vue-router'
import {studentRoutes} from '../src/router/student-routes.js'
import {engineeringRoutes} from '../src/router/engineering-routes.js'

// 两个实验入口的权威定义来自工程路由表与首页/回放页的“实验入口”导航，不能凭记忆另造路径。
const flatten=(routes,parent='')=>routes.flatMap(route=>{const path=route.path.startsWith('/')?route.path:[parent.replace(/\/$/,''),route.path].filter(Boolean).join('/')||'/';return[{...route,path},...(route.children?flatten(route.children,path):[])]})
const sourceOf=route=>typeof route.component==='function'?String(route.component):''
const stub=routes=>routes.map(r=>({...r,...(r.component?{component:{render:()=>null}}:{}),...(r.children?{children:stub(r.children)}:{}),...(r.redirect?{redirect:r.redirect}:{})}))
async function resolved(routes,path){const router=createRouter({history:createMemoryHistory(),routes:stub(routes)});await router.push(path);return router.currentRoute.value}
// 父路由的空路径子项会与父路由同名，保留先出现的父级定义，避免用重定向项覆盖能力声明。
const byPath={};for(const route of flatten(engineeringRoutes))if(!(route.path in byPath))byPath[route.path]=route

test('engineering entry table keeps both experiment entries and their underlying pages',async()=>{
  assert.match(sourceOf(byPath['/super-agent']),/Agent\.vue/)
  assert.match(sourceOf(byPath['/campus']),/ControlledLayout\.vue/)
  assert.match(sourceOf(byPath['/campus/run']),/CampusRun\.vue/)
  assert.match(sourceOf(byPath['/campus/history']),/CampusHistory\.vue/)
  assert.match(sourceOf(byPath['/campus/conversation']),/CampusConversation\.vue/)
  assert.match(sourceOf(byPath['/campus/login']),/ControlledLogin\.vue/)
  assert.match(sourceOf(byPath['/engineering/rules-replay']),/RuleCheckReplayView\.vue/)
  assert.match(sourceOf(byPath['/rules']),/RuleCheckView\.vue/)
  assert.match(sourceOf(byPath['/']),/Home\.vue/)
})

test('engineering router resolves both experiment entries to their own pages',async()=>{
  assert.equal((await resolved(engineeringRoutes,'/super-agent')).path,'/super-agent')
  assert.equal((await resolved(engineeringRoutes,'/campus/run')).path,'/campus/run')
  assert.equal((await resolved(engineeringRoutes,'/campus/history')).path,'/campus/history')
  assert.equal((await resolved(engineeringRoutes,'/campus/conversation')).path,'/campus/conversation')
  assert.equal((await resolved(engineeringRoutes,'/engineering/rules-replay')).path,'/engineering/rules-replay')
  // 工程模式的受控入口仍要求身份，不能因学生隔离而放宽到匿名可用。
  assert.equal(byPath['/campus'].meta.requiresControlledAuth,true)
})

test('experiment entry navigation is still declared in the engineering pages',async()=>{
  const nav=async(file,label)=>{const html=await readFile(new URL(`../src/views/${file}`,import.meta.url),'utf8');return new RegExp(`<nav[^>]*aria-label="${label}"[^>]*>[\\s\\S]*?</nav>`).exec(html)?.[0]||''}
  const home=await nav('Home.vue','实验入口')
  assert.match(home,/to="\/campus\/run"/)
  assert.match(home,/to="\/super-agent"/)
  const replay=await nav('RuleCheckReplayView.vue','查证导航')
  assert.match(replay,/to="\/campus\/run"/)
  assert.match(replay,/to="\/super-agent"/)
})

test('student direct access to either experiment entry returns to rule check',async()=>{
  for(const path of ['/campus/run','/super-agent','/campus/history','/campus/conversation','/campus/login','/engineering/rules-replay','/unlisted-entry']){
    const current=await resolved(studentRoutes,path)
    assert.equal(current.path,'/rules',path)
    assert.equal(current.name,'RuleCheck',path)
    assert.equal(current.query.entry,'rules',path)
  }
  const home=await resolved(studentRoutes,'/')
  assert.equal(home.path,'/rules')
  assert.equal(home.name,'RuleCheck')
})

test('student table never reaches an engineering page',()=>{
  const specs=flatten(studentRoutes).map(sourceOf).join('\n')
  assert.notEqual(specs.trim(),'')
  for(const view of ['Home.vue','Agent.vue','ControlledLogin.vue','CampusRun.vue','CampusHistory.vue','CampusConversation.vue','RuleCheckReplayView.vue','ControlledLayout.vue'])
    assert.doesNotMatch(specs,new RegExp(view.replace('.','\\.')),view)
})
