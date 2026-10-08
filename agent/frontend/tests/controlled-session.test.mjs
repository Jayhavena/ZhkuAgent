import test from 'node:test'
import assert from 'node:assert/strict'
import { createControlledSession } from '../src/state/controlled-session.js'

test('identity invalidation clears actor and token and advances generation', () => {
  const session = createControlledSession()
  session.state.actor = { userId: 'synthetic-A', role: 'USER' }
  session.csrf = { token: 'test-token' }
  session.clear('AUTH_EXPIRED')
  assert.equal(session.state.actor, null)
  assert.equal(session.csrf, null)
  assert.equal(session.state.epoch, 1)
})

test('clear aborts registered readers and invalidates delivery ticket', () => {
  const session = createControlledSession()
  const scope = session.acquire()
  assert.equal(scope.current(), true)
  session.clear()
  assert.equal(scope.controller.signal.aborted, true)
  assert.equal(scope.current(), false)
  scope.release()
})
