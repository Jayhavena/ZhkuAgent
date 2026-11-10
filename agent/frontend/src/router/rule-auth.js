// 只允许回到规则页的站内位置，避免把登录后的导航变成开放重定向。
export function safeRuleReturn(value) {
  if (typeof value !== 'string' || !/^\/rules(?:[?#]|$)/.test(value) || value.includes('\\')) return '/rules'
  return value
}

export function createRuleAuthGuard({ session, client }) {
  return async to => {
    if (!to.meta.requiresRuleAuth || session.state.actor) return
    try { await client.me() }
    catch (error) {
      if (error.code === 'STALE_RESPONSE' || error.code === 'AUTH_TRANSITION') return false
      return { path: '/login', query: { return: safeRuleReturn(to.fullPath) } }
    }
  }
}
