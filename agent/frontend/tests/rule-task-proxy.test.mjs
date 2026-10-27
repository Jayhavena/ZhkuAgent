import test from 'node:test'
import assert from 'node:assert/strict'
import { ruleTaskProxy, RULE_TASK_PREFIX } from '../rule-task-proxy.js'

test('task proxy is opt-in and preserves the entire API path and origin', () => {
  assert.deepEqual(ruleTaskProxy(), {})
  assert.deepEqual(ruleTaskProxy('http://127.0.0.1:18087'), {
    [RULE_TASK_PREFIX]: { target: 'http://127.0.0.1:18087', changeOrigin: false }
  })
})
test('task proxy refuses remote origins, credentials, paths and implicit ports', () => {
  for (const target of ['https://example.org:443', 'http://192.168.1.2:8080', 'http://127.0.0.1',
    'http://user:pass@127.0.0.1:8080', 'http://127.0.0.1:8080/api', 'http://localhost:8080/?x=1', 'http://localhost:8080/#x']) {
    assert.throws(() => ruleTaskProxy(target))
  }
})
