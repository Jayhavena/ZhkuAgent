import { createControlledSession } from './controlled-session.js'
import { createIdentityBus } from './identity-bus.js'
import { ruleTaskHistory } from './rule-task-history.js'

// 复用换代机制，不共享旧学分入口的主体、Cookie资格或任务持久化。
export const ruleCheckSession = createControlledSession()

// 把会话的换代接到跨窗口通道上。本地已经持有身份时的换代才算对外可见的“身份变化”，
// 未登录状态的重复清理不应打扰其他窗口。
export function bindIdentityBus(session = ruleCheckSession, bus = createIdentityBus()) {
  const local = session.clear.bind(session)
  session.clear = reason => {
    const hadIdentity = session.state.actor !== null
    // 短暂失效只保留账号绑定ID，便于同账号重新鉴权；明确退出/身份切换必须清缓存。
    if (reason !== 'AUTH_EXPIRED' && (hadIdentity || reason)) ruleTaskHistory.clear()
    local(reason)
    if (hadIdentity) bus.publish({ reason })
  }
  // 接收端必须走未包装的clear：否则收到别的窗口的换代后又广播回去，两个窗口会互相无限换代。
  const unsubscribe = bus.subscribe(() => { ruleTaskHistory.clear(); local('IDENTITY_CHANGED_ELSEWHERE') })
  return { bus, dispose() { unsubscribe(); bus.close() } }
}

const identityBinding = bindIdentityBus()
// 本窗口原本匿名也可能覆盖同源其他窗口的 Cookie，登录确认后仍须通知它们清理旧结果。
export function broadcastRuleLogin() { identityBinding.bus.publish({ reason: 'IDENTITY_CHANGED' }) }
