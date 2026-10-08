import test from 'node:test'
import assert from 'node:assert/strict'
import { runFacts, historyFacts, submissionFacts, safeReturnPath, stateLabel, visibilityMessage } from '../src/state/controlled-results.js'

test('submission projection matches actual live/archived contract and hides unconfirmed cached body', () => {
  const live = submissionFacts({ runId: 'x', location: 'LIVE', live: { run: { state: 'SUCCEEDED', status: 'ANSWERED', answer: '旧正文' }, resultVisibility: 'HIDDEN' } })
  assert.equal(live.answer, null)
  const unknown = submissionFacts({ runId: 'x', location: 'UNCONFIRMED', answer: '旧正文' })
  assert.equal(unknown.answer, null); assert.equal(unknown.terminated, undefined)
  assert.equal(stateLabel(unknown), '执行情况暂未确认')
})
test('interrupted and admitted archive facts do not invent Runtime termination or running state', () => {
  for (const archiveStatus of ['INTERRUPTED', 'ADMITTED']) {
    const facts = submissionFacts({ location: 'ARCHIVED', archived: { archiveStatus, execution: null, result: { answer: '旧正文' } } })
    assert.equal(facts.answer, null); assert.equal(facts.terminated, undefined)
    assert.equal(stateLabel(facts), archiveStatus === 'INTERRUPTED' ? '处理已中断' : '执行情况暂未确认')
    assert.equal(visibilityMessage(facts), archiveStatus === 'INTERRUPTED' ? '本次处理已中断，未自动继续。需要重新核对时请明确新建任务。' : '已记录，执行情况暂未确认。请确认本次提交，不要直接新建重复任务。')
  }
})

test('online hidden or unconfirmed never renders cached body despite SUCCEEDED', () => {
  for (const resultVisibility of ['HIDDEN', 'UNCONFIRMED', 'RUNNING', 'NOT_APPLICABLE']) {
    const facts = runFacts({ run: { state: 'SUCCEEDED', status: 'ANSWERED', answer: '合成缓存正文', source: {} }, resultVisibility })
    assert.equal(facts.answer, null)
    assert.equal(facts.source, null)
  }
})
test('cancelled response with terminated false is not finished cleanup', () => {
  const facts = runFacts({ run: { state: 'CANCELLED', terminated: false, stopReason: 'USER_REQUEST' }, resultVisibility: 'NOT_APPLICABLE' })
  assert.equal(facts.terminated, false)
  assert.equal(facts.answer, null)
})
test('history requires confirmed final archive and current visible permission', () => {
  for (const archiveStatus of ['ADMITTED', 'FINALIZED']) {
    const facts = historyFacts({ archiveStatus, execution: { state: 'SUCCEEDED', businessStatus: 'ANSWERED' },
      resultVisibility: { status: archiveStatus === 'FINALIZED' ? 'HIDDEN' : 'VISIBLE' }, result: { answer: '合成缓存正文', source: {} } })
    assert.equal(facts.answer, null)
    assert.equal(facts.source, null)
  }
})
test('visible real response is text plus response source, without fabricated total', () => {
  const source = { sourceTitle: '合成来源', sourceVersion: 'v1', sourceLocator: '第1条' }
  const facts = runFacts({ run: { state: 'SUCCEEDED', status: 'ANSWERED', answer: '合计3.5学分', source }, resultVisibility: 'VISIBLE' })
  assert.equal(facts.answer, '合计3.5学分')
  assert.deepEqual(facts.source, source)
  assert.equal('totalCredits' in facts, false)
})
test('return route permits only internal campus pages', () => {
  assert.equal(safeReturnPath('/campus/history?runId=synthetic'), '/campus/history?runId=synthetic')
  for (const path of ['https://example.invalid', '//example.invalid', '/super-agent', '/campus/login', '/campus/run\\evil']) {
    assert.equal(safeReturnPath(path), '/campus/run')
  }
})

test('cancel and timeout labels wait for termination instead of promising completion', () => {
  const pending = runFacts({ run: { state: 'CANCELLED', terminated: false, stopReason: 'USER_REQUEST' }, resultVisibility: 'NOT_APPLICABLE' })
  assert.equal(stateLabel(pending), '正在取消')
  assert.equal(stateLabel({ ...pending, terminated: true }), '已取消')
  assert.equal(stateLabel({ ...pending, stopReason: 'RUN_TIMEOUT' }), '处理超时，正在停止')
  assert.equal(stateLabel({ ...pending, stopReason: 'RUN_TIMEOUT', terminated: true }), '处理超时')
})
test('generated online result and finalized history do not invent the same cleanup signal', () => {
  assert.equal(stateLabel({ state: 'SUCCEEDED', terminated: false }), '结果已生成')
  const history = historyFacts({ archiveStatus: 'FINALIZED', execution: { state: 'SUCCEEDED', businessStatus: 'ANSWERED' }, resultVisibility: { status: 'HIDDEN' } })
  assert.equal('terminated' in history, false)
  assert.equal(stateLabel(history), '已完成')
  assert.equal(history.answer, null)
})
test('hidden and unconfirmed results explain access without calling execution a failure', () => {
  assert.equal(visibilityMessage({ state: 'SUCCEEDED', visibility: 'HIDDEN' }), '当前参考资料已不可查看，因此暂不展示答案。你可以向教务部门核实。')
  assert.equal(visibilityMessage({ state: 'SUCCEEDED', visibility: 'UNCONFIRMED' }), '结果是否保存成功暂未确认，请稍后刷新；不要重复提交。')
})
test('cancelled and incomplete tasks offer relevant next actions rather than waiting for an answer', () => {
  assert.equal(visibilityMessage({ state: 'CANCELLED', terminated: false }), '正在停止处理，请稍候。')
  assert.equal(visibilityMessage({ state: 'CANCELLED', terminated: true }), '本次任务已取消，需要核对其他课程时可以新建任务。')
  assert.equal(visibilityMessage({ state: 'INCOMPLETE' }), '本次未能完成核对，请补充课程信息或向教务部门核实。')
})
