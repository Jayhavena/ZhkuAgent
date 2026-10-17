import test from 'node:test'
import assert from 'node:assert/strict'
import {createRouter,createMemoryHistory} from 'vue-router'
import {selectEntryRoutes} from '../src/router/entry-routes.js'

test('replay entry uses synchronous replay page at the existing rules URL',async()=>{
  const routes=selectEntryRoutes('replay')
  assert.match(String(routes.find(r=>r.path==='/rules').component),/RuleCheckReplayView/)
  for(const path of ['/','/rules','/engineering/rules-replay','/campus/run','/super-agent']){
    const router=createRouter({history:createMemoryHistory(),routes:routes.map(r=>r.component?{...r,component:{render:()=>null}}:r)})
    await router.push(path)
    assert.equal(router.currentRoute.value.path,'/rules')
  }
})
test('student and engineering entries retain the asynchronous task page',()=>{
  for(const mode of [undefined,'student','engineering']){
    assert.match(String(selectEntryRoutes(mode).find(r=>r.path==='/rules').component),/RuleCheckView\.vue/)
  }
  assert.ok(selectEntryRoutes('engineering').some(r=>r.path==='/super-agent'))
  assert.equal(selectEntryRoutes('replay').some(r=>r.path==='/super-agent'),false)
})