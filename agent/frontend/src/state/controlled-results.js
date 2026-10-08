import { ControlledError, isResourceId } from '../api/controlled.js'
export function submissionFacts(reply) {
  if (reply?.location === 'LIVE') return { ...runFacts(reply.live), location: 'LIVE' }
  if (reply?.location === 'ARCHIVED') return { ...historyFacts(reply.archived), location: 'ARCHIVED' }
  if (reply?.location === 'UNCONFIRMED') return { runId: reply.runId, location: 'UNCONFIRMED', visibility: 'UNCONFIRMED', answer: null, source: null }
  throw new ControlledError('BAD_RESPONSE')
}

export function runFacts(reply) {
  const run = reply?.run
  if (!run || typeof run !== 'object') throw new ControlledError('BAD_RESPONSE')
  const visible = reply.resultVisibility === 'VISIBLE' && run.state === 'SUCCEEDED' && run.status === 'ANSWERED'
  return { runId: run.runId, state: run.state, status: run.status, stopReason: run.stopReason,
    failureCategory: run.failureCategory, terminated: run.terminated === true, visibility: reply.resultVisibility,
    answer: visible && typeof run.answer === 'string' ? run.answer : null,
    source: visible ? run.source ?? null : null,
    steps: Array.isArray(run.steps) ? run.steps.map(({ number, name, status }) => ({ number, name, status })) : [] }
}
export function historyFacts(detail) {
  if (!detail || !isResourceId(detail.runId) && detail.runId !== undefined) throw new ControlledError('BAD_RESPONSE')
  const execution = detail.execution
  const visibility = detail.resultVisibility?.status
  const visible = detail.archiveStatus === 'FINALIZED' && execution?.state === 'SUCCEEDED'
    && execution?.businessStatus === 'ANSWERED' && visibility === 'VISIBLE'
  return { runId: detail.runId, state: execution?.state, status: execution?.businessStatus,
    failureCategory: execution?.failureCategory, stopReason: execution?.stopReason,
    archiveStatus: detail.archiveStatus, visibility,
    answer: visible && typeof detail.result?.answer === 'string' ? detail.result.answer : null,
    source: visible ? detail.result?.source ?? null : null, admittedAt: detail.admittedAt,
    closedAt: detail.closedAt, provenance: detail.provenance }
}
export function safeReturnPath(value) {
  if (typeof value !== 'string' || /[\\\x00-\x1f]/.test(value)) return '/campus/run'
  const url = new URL(value, 'http://campus.local')
  return url.origin === 'http://campus.local' && /^\/campus\/(run|history|conversation)$/.test(url.pathname)
    ? url.pathname + url.search : '/campus/run'
}
export function stateLabel(facts) {
  if (facts?.archiveStatus === 'INTERRUPTED') return '处理已中断'
  if (facts?.archiveStatus === 'ADMITTED' || facts?.location === 'UNCONFIRMED') return '执行情况暂未确认'
  // 取消返回不代表执行已经结束；历史详情没有清理信号，不能把缺少字段当成仍在运行。
  if (facts?.stopReason === 'RUN_TIMEOUT') return facts.terminated === false ? '处理超时，正在停止' : '处理超时'
  if (facts?.state === 'CANCELLED' && facts.terminated === false) return '正在取消'
  if (facts?.state === 'SUCCEEDED' && facts.terminated === false) return '结果已生成'
  return { NEW: '已受理', RUNNING: '正在核对', STOP_REQUESTED: '正在停止', SUCCEEDED: '已完成',
    INCOMPLETE: '未能完成核对', FAILED: '处理失败', CANCELLED: '已取消' }[facts?.state] || '正在查询'
}
export function visibilityMessage(facts) {
  if (facts?.archiveStatus === 'INTERRUPTED') return '本次处理已中断，未自动继续。需要重新核对时请明确新建任务。'
  if (facts?.archiveStatus === 'ADMITTED') return '已记录，执行情况暂未确认。请确认本次提交，不要直接新建重复任务。'
  if (facts?.visibility === 'HIDDEN') return '当前参考资料已不可查看，因此暂不展示答案。你可以向教务部门核实。'
  if (facts?.visibility === 'UNCONFIRMED' || facts?.archiveStatus && facts.archiveStatus !== 'FINALIZED') return '结果是否保存成功暂未确认，请稍后刷新；不要重复提交。'
  if (facts?.status === 'NEED_CLARIFICATION') return '请补充完整的课程编号、类别和学分，再发起新任务。'
  if (facts?.stopReason === 'RUN_TIMEOUT') return facts.terminated === false ? '本次处理超过时限，正在停止。请稍候再查看。' : '本次处理超过时限，请向教务部门核实，或稍后新建任务。'
  if (facts?.state === 'CANCELLED') return facts.terminated === false ? '正在停止处理，请稍候。' : '本次任务已取消，需要核对其他课程时可以新建任务。'
  if (facts?.state === 'INCOMPLETE') return '本次未能完成核对，请补充课程信息或向教务部门核实。'
  if (facts?.state === 'FAILED') return '本次未能核验资料或计算，请向教务部门人工核实。'
  return '核对完成后，答案和参考依据会显示在这里。'
}
