import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

// 跨语言契约的最低成本守卫：Java 的 ReasonCode→IssueKind 映射与前端白名单必须逐项一致，
// 否则新增 reasonCode 会让前端整份拒绝合法结果（E13 的 UNSUPPORTED_DEMAND 就是这种风险）。
const javaSource = await readFile(new URL('../../../knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleAgentContract.java', import.meta.url), 'utf8')
const jsSource = await readFile(new URL('../src/api/rule-agent-contract.js', import.meta.url), 'utf8')

function javaReasons() {
  const body = javaSource.slice(javaSource.indexOf('enum ReasonCode {'))
  const constants = body.slice('enum ReasonCode {'.length, body.indexOf(';'))
  const map = {}
  for (const raw of constants.split(/[,\n]/)) {
    const entry = raw.trim()
    if (!entry) continue
    const m = /^([A-Z][A-Z0-9_]*)(?:\(\s*IssueKind\.([A-Z_]+)\s*\))?$/.exec(entry)
    assert.ok(m, `无法解析 Java ReasonCode: ${entry}`)
    map[m[1]] = m[2] ?? 'PROCESSING_FAILURE'
  }
  return map
}

function jsReasons() {
  const start = jsSource.indexOf('const reasons={')
  const end = jsSource.indexOf('\n}', start)
  const body = jsSource.slice(start + 'const reasons={'.length, end)
  const map = {}
  for (const m of body.matchAll(/([A-Z][A-Z0-9_]*)\s*:\s*'([A-Z_]+)'/g)) map[m[1]] = m[2]
  return map
}

test('Java ReasonCode 与前端白名单逐项一致', () => {
  assert.deepEqual(jsReasons(), javaReasons())
})

test('共享 fixture 的资料不足终态可被前端解析且语义完整', async () => {
  const data = JSON.parse(await readFile(new URL('../../../knowledge/src/test/resources/ruleagent/contract-v3-fixtures.json', import.meta.url), 'utf8'))
  const row = data.snapshots.find(x => x.name === 'honest-unsupported-demand')
  assert.ok(row, '缺少 honest-unsupported-demand 快照')
  const { validateRuleAgentSnapshot } = await import('../src/api/rule-agent-contract.js')
  assert.equal(validateRuleAgentSnapshot(row.value), row.value)
  assert.equal(row.value.taskStatus, 'RESOLVED')
  assert.equal(row.value.result.coverage, 'NONE')
  const issue = row.value.result.items[0].issues[0]
  assert.equal(issue.kind, 'DATA_GAP')
  assert.equal(issue.reasonCode, 'UNSUPPORTED_DEMAND')
  assert.ok(issue.message.length > 0 && issue.nextStep.length > 0)
})
