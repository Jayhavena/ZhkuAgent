import { reactive } from 'vue'

export function createControlledSession() {
  const state = reactive({ actor: null, epoch: 0, checked: false, reason: '', identityBusy: false })
  const active = new Set()
  return {
    state, csrf: null,
    clear(reason = '') {
      // Cookie由服务端管理；本地换代立即清内容并作废旧请求，不能仅依赖Abort终止网络。
      state.epoch++
      for (const controller of active) controller.abort()
      active.clear()
      state.actor = null; state.checked = false; state.reason = reason; this.csrf = null
    },
    acquire() {
      const epoch = state.epoch
      const controller = new AbortController()
      active.add(controller)
      return { epoch, controller, current: () => epoch === state.epoch && !controller.signal.aborted,
        release: () => active.delete(controller) }
    }
  }
}
export const controlledSession = createControlledSession()
