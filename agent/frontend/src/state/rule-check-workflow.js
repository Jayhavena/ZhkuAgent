import { reactive, watch } from 'vue'
import { ruleCheckClient, ruleCheckErrorMessage } from '../api/rule-check.js'
import { ruleCheckSession } from './rule-check-session.js'
export function createRuleCheckWorkflow({ client = ruleCheckClient, session = ruleCheckSession } = {}) {
  const state = reactive({ question: '', result: null, original: null, busy: false, originalBusy: false, error: '' })
  let generation = 0, originalGeneration = 0, alive = true
  function reset() {
    generation++; originalGeneration++; client.cancelResults()
    state.question = ''; state.result = null; state.original = null; state.busy = false; state.originalBusy = false; state.error = ''
  }
  // 同步清除才能保证换账号后的下一帧不显示旧正文；请求完成还须比对本地轮次。
  const stop = watch(() => session.state.epoch, reset, { flush: 'sync' })
  async function execute(question, conditions, token) {
    if (!alive || state.busy || session.state.identityBusy || session.state.actor?.role !== 'USER') return
    const expected = ++generation, epoch = session.state.epoch
    originalGeneration++; state.question = question; state.result = null; state.original = null; state.originalBusy = false; state.error = ''; state.busy = true
    const current = () => alive && expected === generation && epoch === session.state.epoch
    try { const result = await client.check(question, conditions, token); if (current()) state.result = result }
    catch (error) { if (current() && error.code !== 'STALE_RESPONSE') state.error = ruleCheckErrorMessage(error) }
    finally { if (current()) state.busy = false }
  }
  return {
    state,
    start: question => execute(question, {}),
    async clarify(key, value) {
      const reply = state.result, condition = reply?.clarificationQuestions?.[0]
      if (reply?.status !== 'NEED_CLARIFICATION' || !condition || condition.key !== key || !condition.allowedValues.includes(value)) {
        state.error = '请只确认本轮要求的条件。'; return
      }
      await execute(state.question, { [key]: value }, reply.clarificationToken)
    },
    async openOriginal(token) {
      if (!alive || state.busy || !state.result || session.state.identityBusy) return
      const expected = generation, epoch = session.state.epoch, originalRound = ++originalGeneration
      state.original = null; state.originalBusy = true; state.error = ''
      const current = () => alive && expected === generation && originalRound === originalGeneration && epoch === session.state.epoch
      try { const original = await client.original(token); if (current()) state.original = original }
      catch (error) {
        if (current() && error.code !== 'STALE_RESPONSE') {
          // 明确失效时不能继续挂着“有原文支持”；网络故障本身不证明资料撤回，也不自动重问。
          if (error.status === 404 || error.status === 409) state.result = null
          state.error = ruleCheckErrorMessage(error)
        }
      }
      finally { if (current()) state.originalBusy = false }
    },
    reset,
    dispose() { alive = false; stop(); reset() }
  }
}
