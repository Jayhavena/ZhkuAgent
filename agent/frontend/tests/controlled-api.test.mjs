import test from 'node:test'
import assert from 'node:assert/strict'
import { createControlledClient, PREFIX } from '../src/api/controlled.js'
import { createControlledSession } from '../src/state/controlled-session.js'

const ID = '22222222-2222-4222-8222-222222222222'
const json = (body, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } })

test('login uses form protocol then renews CSRF and confirms current actor', async () => {
  const seen = []
  let tokens = 0
  const session = createControlledSession()
  const client = createControlledClient({ session, fetchImpl: async (url, options) => {
    seen.push({ url, options })
    if (url.endsWith('/csrf')) return json({ headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: `test-${++tokens}` })
    if (url.endsWith('/login')) return json({ userId: ID, role: 'USER', authRevision: 0 })
    return json({ userId: ID, role: 'USER', authRevision: 0 })
  } })
  await client.login('user-a', 'synthetic-password')
  assert.deepEqual(seen.map(x => x.url), ['/auth/csrf', '/auth/login', '/auth/csrf', '/auth/me'].map(x => PREFIX + x))
  assert.equal(seen[1].options.headers['X-CSRF-TOKEN'], 'test-1')
  assert.equal(new URLSearchParams(seen[1].options.body).get('username'), 'user-a')
  assert.equal(session.csrf.token, 'test-2')
  assert.equal(session.state.actor.userId, ID)
  assert.equal(seen.every(x => x.options.credentials === 'same-origin'), true)
})

test('503 accepted preserves runId without repeating POST', async () => {
  let posts = 0
  const session = createControlledSession()
  session.csrf = { headerName: 'X-CSRF-TOKEN', token: 'test-token' }
  const client = createControlledClient({ session, fetchImpl: async () => {
    posts++; return json({ accepted: true, acceptance: 'ACCEPTED', submissionKey: ID, runId: ID, code: 'RESULT_READ_UNAVAILABLE' }, 503)
  } })
  const accepted = await client.startRun({ question: '合成', courses: [] }, { submissionKey: ID })
  assert.equal(accepted.runId, ID)
  assert.equal(accepted.acceptedReadUnavailable, true)
  assert.equal(posts, 1)
})

test('lost POST response is unknown and is never retried', async () => {
  let posts = 0
  const session = createControlledSession()
  session.csrf = { headerName: 'X-CSRF-TOKEN', token: 'test-token' }
  const client = createControlledClient({ session, fetchImpl: async () => { posts++; throw new TypeError('synthetic unavailable') } })
  await assert.rejects(client.startRun({ question: '合成', courses: [] }, { submissionKey: ID }), error => error.submissionUnknown === true)
  assert.equal(posts, 1)
})

test('401 clears identity whereas 403 preserves it', async () => {
  for (const status of [401, 403]) {
    const session = createControlledSession()
    session.state.actor = { userId: ID, role: 'USER' }
    session.csrf = { token: 'test-token' }
    const client = createControlledClient({ session, fetchImpl: async () => json({ code: 'DENIED' }, status) })
    await assert.rejects(client.findRun(ID), error => error.status === status)
    assert.equal(session.state.actor !== null, status === 403)
  }
})

test('late old identity response cannot overwrite new actor even when transport ignores abort', async () => {
  let deliver
  const session = createControlledSession()
  session.state.actor = { userId: ID, role: 'USER' }
  const client = createControlledClient({ session, fetchImpl: () => new Promise(resolve => { deliver = resolve }) })
  const old = client.me()
  session.clear()
  session.state.actor = { userId: '33333333-3333-4333-8333-333333333333', role: 'USER' }
  deliver(json({ userId: ID, role: 'USER', authRevision: 0 }))
  await assert.rejects(old, error => error.code === 'STALE_RESPONSE')
  assert.equal(session.state.actor.userId.startsWith('3333'), true)
})

test('deadline during anonymous CSRF is network failure, not identity change', async () => {
  const session = createControlledSession()
  const client = createControlledClient({ session, timeoutMs: 5, fetchImpl: (url, options) => new Promise((resolve, reject) => {
    options.signal.addEventListener('abort', () => reject(new DOMException('synthetic deadline', 'AbortError')))
  }) })
  await assert.rejects(client.csrf(), error => error.code === 'NETWORK_UNAVAILABLE')
})

test('malformed successful POST is unknown and malformed GET hides result', async () => {
  const session = createControlledSession()
  session.csrf = { headerName: 'X-CSRF-TOKEN', token: 'test-token' }
  const client = createControlledClient({ session, fetchImpl: async () => new Response('<html>bad gateway</html>', { status: 200 }) })
  await assert.rejects(client.startRun({ question: '合成', courses: [] }, { submissionKey: ID }), error => error.submissionUnknown === true)
  await assert.rejects(client.findRun(ID), error => error.code === 'BAD_RESPONSE')
})

test('late 401 from previous identity cannot clear new identity', async () => {
  let deliver
  const session = createControlledSession()
  const client = createControlledClient({ session, fetchImpl: () => new Promise(resolve => { deliver = resolve }) })
  const old = client.findRun(ID)
  session.clear(); session.state.actor = { userId: ID, role: 'USER' }
  deliver(json({ code: 'AUTH_REVOKED' }, 401))
  await assert.rejects(old, error => error.code === 'STALE_RESPONSE')
  assert.equal(session.state.actor.userId, ID)
})

test('late logout completion cannot erase a newly logged identity', async () => {
  let deliver
  const session = createControlledSession()
  session.state.actor = { userId: ID, role: 'USER' }
  session.csrf = { headerName: 'X-CSRF-TOKEN', token: 'test-token' }
  const client = createControlledClient({ session, fetchImpl: () => new Promise(resolve => { deliver = resolve }) })
  const old = client.logout()
  session.clear(); session.state.actor = { userId: ID, role: 'USER' }
  deliver(new Response(null, { status: 204 }))
  await assert.rejects(old, error => error.code === 'STALE_RESPONSE')
  assert.equal(session.state.actor?.userId, ID)
})

test('login cannot overlap pending logout and race server Cookie changes', async () => {
  let deliver
  const session = createControlledSession()
  session.csrf = { headerName: 'X-CSRF-TOKEN', token: 'test-token' }
  const client = createControlledClient({ session, fetchImpl: async url => {
    if (url.endsWith('/logout')) return new Promise(resolve => { deliver = resolve })
    if (url.endsWith('/csrf')) return json({ headerName: 'X-CSRF-TOKEN', token: 'test-token' })
    return json({ userId: ID, role: 'USER', authRevision: 0 })
  } })
  const old = client.logout()
  try { await assert.rejects(client.login('user-b', 'fixture-not-a-secret'), error => error.code === 'AUTH_TRANSITION') }
  finally { deliver(new Response(null, { status: 204 })); await old.catch(() => {}) }
})
