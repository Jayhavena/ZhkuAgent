import test from 'node:test'
import assert from 'node:assert/strict'
import { createSubmissionStore } from '../src/state/controlled-submission.js'
import { createControlledClient } from '../src/api/controlled.js'
import { createControlledSession } from '../src/state/controlled-session.js'
const A = '22222222-2222-4222-8222-222222222222', B = '33333333-3333-4333-8333-333333333333'
const KEY = '11111111-1111-4111-8111-111111111111'
function fixture() {
  const data = new Map()
  const storage = { getItem: k => data.get(k) ?? null, setItem: (k, v) => data.set(k, v), removeItem: k => data.delete(k) }
  let sequence = 0
  const options = { storage, randomUUID: () => `${String(++sequence).padStart(8, '0')}-1111-4111-8111-111111111111`, now: () => 1700000000000 }
  return { data, options, store: createSubmissionStore(options) }
}
const body = () => ({ question: '合成课程问题', courses: [{ courseId: 'ART01', category: '公共选修课', credits: 1.5 }] })
test('freeze original request, manual resend retains key and body, refresh persists metadata only', () => {
  const { store, data, options } = fixture(), request = body(), first = store.begin(A, request)
  request.question = '已编辑'; request.courses[0].credits = 99
  assert.equal(first.requestSnapshot.question, '合成课程问题')
  assert.equal(first.requestSnapshot.courses[0].credits, 1.5)
  assert.equal(Object.isFrozen(first.requestSnapshot.courses[0]), true)
  assert.deepEqual(store.restore(A), first)
  const refreshed = createSubmissionStore(options).restore(A)
  assert.equal(refreshed.submissionKey, first.submissionKey)
  assert.equal(refreshed.requestSnapshot, undefined)
  assert.equal([...data.values()].join('').includes('合成课程问题'), false)
  assert.equal([...data.values()].join('').includes('公共选修课'), false)
})
test('at most five hints, new task new key, logout and switch erase old identity hints', () => {
  const { store, data } = fixture()
  const keys = Array.from({ length: 7 }, () => store.begin(A, body()).submissionKey)
  assert.equal(new Set(keys).size, 7)
  assert.equal(JSON.parse([...data.values()][0]).entries.length, 5)
  assert.equal(store.restore(B), null)
  assert.equal(store.restore(A), null)
  store.begin(A, body()); store.forget(A)
  assert.equal(store.restore(A), null)
  assert.equal(data.size, 0)
})
test('confirmed expiry and runId survive refresh, stale owner cannot attach reply', () => {
  const { store, options } = fixture(), first = store.begin(A, body())
  store.confirm({ submissionKey: first.submissionKey, runId: A, expiresAt: '2023-11-21T22:13:20Z' })
  assert.equal(createSubmissionStore(options).restore(A).runId, A)
  store.restore(B)
  assert.throws(() => store.confirm({ submissionKey: first.submissionKey, runId: A }), /UNKNOWN_SUBMISSION/)
})
test('no insecure UUID fallback; corrupt storage cannot restore request or results', () => {
  const { store, data, options } = fixture()
  assert.throws(() => createSubmissionStore({ ...options, randomUUID: () => 'not-uuid' }).begin(A, body()), /INVALID_SUBMISSION_KEY/)
  store.begin(A, body())
  data.set([...data.keys()][0], JSON.stringify({ ownerUserId: A, entries: [{ submissionKey: KEY, createdAt: 1700000000000, expiresAt: 1700604800000, requestSnapshot: body(), answer: '旧答案' }] }))
  const restored = createSubmissionStore(options).restore(A)
  assert.equal(restored.requestSnapshot, undefined); assert.equal(restored.answer, undefined)
})
test('one POST with original key, loss followed by GET confirmation never automatic resend', async () => {
  const { store } = fixture(), first = store.begin(A, body()), seen = []
  const session = createControlledSession(); session.csrf = { headerName: 'X-CSRF-TOKEN', token: 'fixture' }
  const client = createControlledClient({ session, submissions: store, fetchImpl: async (url, options) => {
    seen.push({ url, ...options })
    if (options.method === 'POST') throw new TypeError('lost response')
    return new Response(JSON.stringify({ submissionKey: first.submissionKey, runId: A, replayed: true,
      acceptedAt: '2023-11-14T22:13:20Z', expiresAt: '2023-11-21T22:13:20Z', location: 'UNCONFIRMED', live: null, archived: null }))
  } })
  await assert.rejects(client.startRun(first.requestSnapshot, { submissionKey: first.submissionKey }), e => e.submissionUnknown)
  const confirmed = await client.findSubmission(first.submissionKey)
  assert.equal(confirmed.runId, A)
  assert.equal(seen.filter(x => x.method === 'POST').length, 1)
  assert.equal(seen[0].headers['Idempotency-Key'], first.submissionKey)
  assert.equal(seen[1].url.endsWith('/submissions/' + first.submissionKey), true)
})
test('invalid/missing key performs zero HTTP; late confirmation cannot affect new identity', async () => {
  const { store } = fixture(); store.begin(A, body())
  const session = createControlledSession(); session.csrf = { headerName: 'X-CSRF-TOKEN', token: 'fixture' }
  let count = 0, deliver
  const client = createControlledClient({ session, submissions: store, fetchImpl: () => { count++; return new Promise(resolve => { deliver = resolve }) } })
  await assert.rejects(client.startRun(body()), e => e.code === 'INVALID_SUBMISSION_KEY')
  assert.equal(count, 0)
  const old = client.findSubmission(KEY); session.clear(); session.state.actor = { userId: B, role: 'USER' }; store.restore(B)
  deliver(new Response(JSON.stringify({ submissionKey: KEY, runId: A, location: 'UNCONFIRMED' })))
  await assert.rejects(old, e => e.code === 'STALE_RESPONSE')
  assert.equal(store.restore(B), null)
})
test('explicit retry uses frozen original and the same header, not edited form content', async () => {
  const { store } = fixture(), original = store.begin(A, body()), sent = []
  const session = createControlledSession(); session.csrf = { headerName: 'X-CSRF-TOKEN', token: 'fixture' }
  const client = createControlledClient({ session, submissions: store, fetchImpl: async (url, options) => {
    sent.push(options)
    if (sent.length === 1) throw new TypeError('lost')
    return new Response(JSON.stringify({ submissionKey: original.submissionKey, runId: A, replayed: true,
      acceptedAt: '2023-11-14T22:13:20Z', expiresAt: '2023-11-21T22:13:20Z', location: 'LIVE', archived: null,
      live: { run: { runId: A, state: 'SUCCEEDED', status: 'ANSWERED' }, resultVisibility: 'HIDDEN' } }))
  } })
  await assert.rejects(client.startRun(original.requestSnapshot, { submissionKey: original.submissionKey }))
  const retained = store.restore(A)
  await client.startRun(retained.requestSnapshot, { submissionKey: retained.submissionKey })
  assert.equal(sent.length, 2); assert.equal(sent[0].body, sent[1].body)
  assert.equal(sent[0].headers['Idempotency-Key'], sent[1].headers['Idempotency-Key'])
})
test('401 keeps confirmation metadata for same-user reauthentication; explicit logout removes it', async () => {
  const { store } = fixture(), original = store.begin(A, body())
  const session = createControlledSession(); session.state.actor = { userId: A, role: 'USER' }
  let expired = true
  const client = createControlledClient({ session, submissions: store, fetchImpl: async url => {
    if (url.endsWith('/csrf')) return new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'fixture' }))
    if (url.endsWith('/logout')) return new Response(null, { status: 204 })
    if (expired) return new Response(JSON.stringify({ code: 'AUTH_REVOKED' }), { status: 401 })
    return new Response(JSON.stringify({ userId: A, role: 'USER', authRevision: 0 }))
  } })
  await assert.rejects(client.findSubmission(original.submissionKey), e => e.status === 401)
  assert.equal(session.state.actor, null); assert.equal(store.restore(A).submissionKey, original.submissionKey)
  expired = false; await client.login('user-a', 'synthetic-fixture')
  assert.equal(store.restore(A).submissionKey, original.submissionKey)
  await client.logout(); assert.equal(store.restore(A), null)
})
