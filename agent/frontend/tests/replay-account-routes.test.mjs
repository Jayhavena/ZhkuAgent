import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { createMemoryHistory, createRouter } from 'vue-router'
import { selectEntryRoutes } from '../src/router/entry-routes.js'
import { ruleCheckSession } from '../src/state/rule-check-session.js'

const routes = selectEntryRoutes('replay')
const stub = () => routes.map(route => route.component ? { ...route, component: { render: () => null } } : route)

test('replay exposes formal account routes while retaining its synchronous page', async () => {
  assert.match(String(routes.find(r => r.path === '/rules').component), /RuleCheckReplayView/)
  for (const [path, mode] of [['/login','login'], ['/register','register'], ['/account','account']]) {
    const route = routes.find(r => r.path === path)
    assert.match(String(route.component), /RuleAccount/)
    assert.equal(route.meta.accountMode, mode)
  }
  const source = await readFile(new URL('../src/views/RuleCheckReplayView.vue', import.meta.url), 'utf8')
  for (const path of ['/login', '/register', '/account']) assert.ok(source.includes(`to="${path}"`))
  assert.match(source, /createRuleCheckWorkflow/)
})

test('direct account navigation restores cookie identity or redirects safely', async () => {
  const originalFetch = globalThis.fetch
  const calls = []
  try {
    ruleCheckSession.clear()
    globalThis.fetch = async (url, options) => {
      calls.push([url, options])
      return new Response(null, { status: 401 })
    }
    let router = createRouter({ history: createMemoryHistory(), routes: stub() })
    await router.push('/account?return=//evil.test')
    assert.equal(router.currentRoute.value.path, '/login')
    assert.equal(router.currentRoute.value.query.return, '/rules')
    assert.equal(calls[0][0], '/api/zhku/controlled-preview/v2/auth/me')
    assert.equal(calls[0][1].credentials, 'same-origin')
    globalThis.fetch = async () => new Response(JSON.stringify({ userId:'22222222-2222-4222-8222-222222222222', role:'USER', authRevision:1 }), { status:200, headers:{'Content-Type':'application/json'} })
    router = createRouter({ history: createMemoryHistory(), routes: stub() })
    await router.push('/account')
    assert.equal(router.currentRoute.value.path, '/account')
    assert.equal(ruleCheckSession.state.actor.role, 'USER')
  } finally {
    globalThis.fetch = originalFetch
    ruleCheckSession.clear()
  }
})
