import test from 'node:test'
import assert from 'node:assert/strict'
import { createRuleCheckClient, PREFIX } from '../src/api/rule-check.js'
import { createControlledSession } from '../src/state/controlled-session.js'
import { createRuleAuthGuard, safeRuleReturn } from '../src/router/rule-auth.js'
const actor = { userId:'22222222-2222-4222-8222-222222222222', role:'USER', authRevision:1, username:'student', accountStatus:'ACTIVE', createdAt:'2026-01-01T00:00:00Z' }
const json = data => new Response(JSON.stringify(data), { status:200 })

test('registration and password use v2 JSON and CSRF; remember-me is explicit', async () => {
  const calls=[], session=createControlledSession()
  const client=createRuleCheckClient({session,fetchImpl:async(url,options)=>{
    calls.push({url,options})
    if(url.endsWith('/csrf')) return json({headerName:'X-CSRF-TOKEN',token:'csrf'})
    if(url.endsWith('/me')) return json(actor)
    return new Response(null,{status:204})
  }})
  await client.register('student','secret','secret')
  const registration=calls.find(x=>x.url.endsWith('/register'))
  assert.equal(registration.url,PREFIX+'/auth/register'); assert.equal(registration.options.headers['X-CSRF-TOKEN'],'csrf')
  assert.deepEqual(JSON.parse(registration.options.body),{username:'student',password:'secret',confirmPassword:'secret'})
  await client.login('student','secret',true)
  assert.equal(new URLSearchParams(calls.find(x=>x.url.endsWith('/login')).options.body).get('remember-me'),'true')
  assert.deepEqual(session.state.actor,actor)
  await client.changePassword('secret','new-secret','new-secret')
  const change=calls.find(x=>x.url.endsWith('/password'))
  assert.equal(change.options.method,'PUT'); assert.deepEqual(JSON.parse(change.options.body),{currentPassword:'secret',newPassword:'new-secret',confirmPassword:'new-secret'})
  assert.equal(session.state.actor,null)
})
test('account details must belong to the current identity and stale results are rejected', async () => {
  const session = createControlledSession(); session.state.actor = actor
  let release
  const client = createRuleCheckClient({ session, fetchImpl: () => new Promise(resolve => { release = resolve }) })
  const pending = client.account()
  session.clear('IDENTITY_CHANGED_ELSEWHERE')
  release(json({ userId: actor.userId, loginName: 'student' }))
  await assert.rejects(pending, { code: 'STALE_RESPONSE' })
  session.state.actor = actor
  const mismatch = createRuleCheckClient({ session, fetchImpl: async () => json({ userId: '33333333-3333-4333-8333-333333333333', loginName: 'other' }) })
  await assert.rejects(mismatch.account(), { code: 'BAD_RESPONSE' })
})
test('password response loss invalidates identity', async () => {
  const session = createControlledSession(); session.state.actor = actor
  session.csrf = { headerName: 'X-CSRF-TOKEN', token: 'csrf' }
  const client = createRuleCheckClient({ session, fetchImpl: async () => { throw new Error('network') } })
  await assert.rejects(client.changePassword('old', 'new', 'new'), { code: 'NETWORK_UNAVAILABLE' })
  assert.equal(session.state.actor, null)
})
test('guard restores cookie identity and retains rule location; rejects unsafe returns',async()=>{
  const session=createControlledSession(), to={meta:{requiresRuleAuth:true},fullPath:'/rules?q=1#answer'}
  const guard=createRuleAuthGuard({session,client:{me:async()=>{session.state.actor=actor}}})
  assert.equal(await guard(to),undefined);assert.equal(session.state.actor.username,'student')
  session.clear()
  const fail=createRuleAuthGuard({session,client:{me:async()=>{throw Object.assign(new Error(),{status:401})}}})
  assert.deepEqual(await fail(to),{path:'/login',query:{return:'/rules?q=1#answer'}})
  for(const value of ['//evil.test','/rules.evil','/rules\\evil','https://evil.test']) assert.equal(safeRuleReturn(value),'/rules')
})
