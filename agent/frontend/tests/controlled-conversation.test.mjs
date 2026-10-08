import test from 'node:test'
import assert from 'node:assert/strict'
import { createControlledClient, ControlledError, PREFIX } from '../src/api/controlled.js'
import { createControlledSession } from '../src/state/controlled-session.js'
import { createConversationPage, conversationSnapshot, conversationReply } from '../src/state/controlled-conversation.js'

const ID = '33333333-3333-4333-8333-333333333333'
const OTHER = '44444444-4444-4444-8444-444444444444'
const snapshot = (revision = 0, pending = false, status = 'ACTIVE') => ({ sessionId: ID, status, revision,
  pendingIntent: pending ? 'ELECTIVE_RETAKE_AFTER_FAILURE' : null, pendingField: pending ? 'COURSE_TYPE' : null,
  expiresAt: '2026-10-06T12:00:00Z' })
const response = (body, status = 200) => new Response(JSON.stringify(body), { status })
const deferred = () => { let resolve; const promise = new Promise(done => { resolve = done }); return { promise, resolve } }
const answer = { type: 'ANSWER', message: '合成答复', answer: { conclusion: '合成重修结论', basis: '只适用合成情形',
  sourceTitle: '合成重修规则', sourceVersion: 'test-v1', sourceLocator: '第1条', applicableScope: '仅测试' } }

test('conversation four APIs send only actual fields with CSRF and explicit revision', async () => {
  const seen = [], session = createControlledSession()
  session.csrf = { headerName: 'X-CSRF-TOKEN', token: 'public-test' }
  const api = createControlledClient({ session, fetchImpl: async (url, options) => { seen.push({ url, ...options }); return response(snapshot()) } })
  await api.createConversation()
  await api.findConversation(ID)
  await api.sendConversationMessage(ID, '专业选修课', 1)
  await api.cancelConversation(ID, 2)
  assert.deepEqual(seen.map(x => x.url), ['/conversations', `/conversations/${ID}`, `/conversations/${ID}/messages`, `/conversations/${ID}/cancel`].map(x => PREFIX + x))
  assert.deepEqual(seen.filter(x => x.method === 'POST').map(x => JSON.parse(x.body)), [{}, { message: '专业选修课', expectedRevision: 1 }, { expectedRevision: 2 }])
  assert.equal(seen.filter(x => x.method === 'POST').every(x => x.headers['X-CSRF-TOKEN'] === 'public-test'), true)
  await assert.rejects(api.sendConversationMessage(ID, '补答', -1), e => e.code === 'INVALID_REVISION')
  assert.equal(seen.length, 4)
})

test('unknown create and write are distinct and never automatically POST again', async () => {
  for (const operation of ['create', 'message', 'cancel']) {
    const session = createControlledSession(); session.csrf = { headerName: 'X-CSRF-TOKEN', token: 'public-test' }
    let calls = 0
    const api = createControlledClient({ session, fetchImpl: async () => { calls++; throw new TypeError('synthetic transport failure') } })
    const request = operation === 'create' ? api.createConversation() : operation === 'message' ? api.sendConversationMessage(ID, '补答', 1) : api.cancelConversation(ID, 1)
    await assert.rejects(request, e => e.conversationUnknown === (operation === 'create' ? 'create' : 'write'))
    assert.equal(calls, 1)
  }
})

test('restore only exact control snapshot; malformed revisions and pending pairs rejected', () => {
  assert.deepEqual(conversationSnapshot({ ...snapshot(1, true), history: ['synthetic old chat'] }, ID), snapshot(1, true))
  for (const value of [{ ...snapshot(), revision: Number.MAX_SAFE_INTEGER + 1 }, { ...snapshot(), sessionId: OTHER },
    { ...snapshot(), pendingField: 'COURSE_TYPE' }, { ...snapshot(), status: 'INVENTED' }]) {
    assert.throws(() => conversationSnapshot(value, ID), e => e.code === 'BAD_RESPONSE')
  }
})

test('evidence unavailable and non-answer types never project a supplied stale answer', () => {
  for (const type of ['EVIDENCE_UNAVAILABLE', 'NEED_CLARIFICATION', 'OUT_OF_SCOPE', 'SESSION_CANCELLED']) {
    assert.equal(conversationReply({ ...answer, type }).answer, null)
  }
  assert.equal(conversationReply(answer).answer.conclusion, '合成重修结论')
})

test('message confirms new revision with GET before publishing current exchange', async () => {
  const events = [], page = createConversationPage({ api: {
    findConversation: async () => { events.push('GET'); return snapshot(events.includes('POST') ? 1 : 0, events.includes('POST')) },
    sendConversationMessage: async (id, message, revision) => { events.push('POST'); assert.equal(revision, 0); return { type: 'NEED_CLARIFICATION', message: '合成追问', answer: null } }
  } })
  await page.load(ID); page.state.draft = '合成重修问题'; await page.send()
  assert.deepEqual(events, ['GET', 'POST', 'GET'])
  assert.equal(page.state.snapshot.revision, 1)
  assert.equal(page.state.reply.type, 'NEED_CLARIFICATION')
  assert.equal(page.state.question, '合成重修问题')
  assert.equal(page.state.draft, '')
})

test('conflict needs explicit read and confirmation, never automatic resubmission', async () => {
  let posts = 0, gets = 0
  const page = createConversationPage({ api: {
    findConversation: async () => { gets++; return snapshot(gets - 1, gets > 1) },
    sendConversationMessage: async () => { posts++; throw new ControlledError('SESSION_STATE_CHANGED', 409) }
  } })
  await page.load(ID); page.state.draft = '合成问题'; await page.send()
  assert.equal(page.state.phase, 'conflict'); assert.equal(posts, 1); assert.equal(gets, 1)
  await page.send(); assert.equal(posts, 1)
  await page.load(ID); assert.equal(page.state.phase, 'confirm'); assert.equal(page.state.snapshot.revision, 1)
  page.confirmLatest(); assert.equal(page.state.phase, 'ready'); assert.equal(posts, 1)
})

test('confirmed POST but follow-up GET failure shows no answer and locks writes', async () => {
  let gets = 0, posts = 0
  const page = createConversationPage({ api: {
    findConversation: async () => { if (++gets > 1) throw new ControlledError('SESSION_STORE_UNAVAILABLE', 503); return snapshot(1, true) },
    sendConversationMessage: async () => { posts++; return answer }
  } })
  await page.load(ID); page.state.draft = '专业选修课'; await page.send()
  assert.equal(page.state.reply, null); assert.equal(page.state.phase, 'writeUnknown')
  await page.send(); assert.equal(posts, 1)
})

test('expired or cancelled control state cannot be revived by send', async () => {
  for (const kind of ['expired', 'cancelled']) {
    let posts = 0
    const page = createConversationPage({ api: {
      findConversation: async () => { if (kind === 'expired') throw new ControlledError('SESSION_EXPIRED', 410); return snapshot(2, false, 'CANCELLED') },
      sendConversationMessage: async () => { posts++ }
    } })
    await page.load(ID); page.state.draft = '补答'; await page.send()
    assert.equal(page.state.phase, kind); assert.equal(page.state.reply, null); assert.equal(posts, 0)
  }
})

test('cancel clears current body and preserves returned revision and expiry', async () => {
  const page = createConversationPage({ api: { findConversation: async () => snapshot(1, true), cancelConversation: async (id, revision) => {
    assert.equal(revision, 1); return snapshot(2, false, 'CANCELLED')
  } } })
  await page.load(ID); page.state.reply = answer; await page.cancel()
  assert.equal(page.state.phase, 'cancelled'); assert.equal(page.state.reply, null)
  assert.equal(page.state.snapshot.revision, 2); assert.equal(page.state.snapshot.expiresAt, snapshot().expiresAt)
})

test('unknown creation stays locked until explicit acknowledgement, no fabricated ID', async () => {
  let posts = 0
  const page = createConversationPage({ api: { createConversation: async () => { posts++; throw new ControlledError('NETWORK_UNAVAILABLE', 0, { conversationUnknown: 'create' }) } } })
  await page.create(); await page.create()
  assert.equal(posts, 1); assert.equal(page.state.phase, 'createUnknown'); assert.equal(page.state.id, null)
  await page.create(true); assert.equal(posts, 2)
})

test('dispose and route change discard late reply even if transport ignores abort', async () => {
  const wait = deferred()
  const page = createConversationPage({ api: { findConversation: async id => id === ID ? wait.promise : { ...snapshot(), sessionId: OTHER } } })
  const old = page.load(ID)
  await page.load(OTHER); wait.resolve(snapshot(1, true)); await old
  assert.equal(page.state.id, OTHER); assert.equal(page.state.snapshot.sessionId, OTHER)
  page.state.reply = answer; page.state.draft = '私有合成输入'; page.dispose()
  assert.equal(page.state.id, null); assert.equal(page.state.reply, null); assert.equal(page.state.draft, '')
})

test('explicit refresh clears old exchange and restores pending without chat history', async () => {
  const page = createConversationPage({ api: { findConversation: async () => snapshot(1, true) } })
  await page.load(ID); page.state.reply = answer; page.state.question = '旧问题'; await page.load(ID)
  assert.equal(page.state.reply, null); assert.equal(page.state.question, '')
  assert.equal(page.state.snapshot.pendingField, 'COURSE_TYPE'); assert.equal(page.state.phase, 'ready')
})

test('recovery GET failure cannot remove the explicit confirmation gate', async () => {
  let gets = 0, posts = 0
  const page = createConversationPage({ api: {
    findConversation: async () => { if (++gets === 2) throw new ControlledError('SESSION_STORE_UNAVAILABLE', 503); return snapshot(1, true) },
    sendConversationMessage: async () => { posts++; throw new ControlledError('SESSION_STATE_CHANGED', 409) }
  } })
  await page.load(ID); page.state.draft = '专业选修课'; await page.send()
  await page.load(ID); await page.load(ID)
  assert.equal(page.state.phase, 'confirm')
  page.state.draft = '专业选修课'; await page.send(); assert.equal(posts, 1)
})

test('late conversation GET after identity invalidation is discarded by real client', async () => {
  const wait = deferred(), session = createControlledSession()
  session.state.actor = { userId: ID, role: 'USER' }
  const api = createControlledClient({ session, fetchImpl: async () => wait.promise })
  const pending = api.findConversation(ID)
  session.clear(); session.state.actor = { userId: OTHER, role: 'USER' }
  wait.resolve(response(snapshot(1, true)))
  await assert.rejects(pending, e => e.code === 'STALE_RESPONSE')
  assert.equal(session.state.actor.userId, OTHER)
})

test('withdrawn evidence replaces prior answer without preserving source', async () => {
  const page = createConversationPage({ api: { findConversation: async () => snapshot(1, true),
    sendConversationMessage: async () => ({ ...answer, type: 'EVIDENCE_UNAVAILABLE', message: '当前依据不可核验' }) } })
  await page.load(ID); page.state.reply = answer; page.state.draft = '专业选修课'; await page.send()
  assert.equal(page.state.reply.answer, null); assert.equal(page.state.reply.type, 'EVIDENCE_UNAVAILABLE')
})
