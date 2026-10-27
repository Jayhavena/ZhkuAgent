export const RULE_TASK_PREFIX = '/api/zhku/controlled-preview/v2'

// 默认无代理；本机验收只能显式绑定回环端口，避免将会话和学生问题误发至远端。
export function ruleTaskProxy(target) {
  if (!target) return {}
  const url = new URL(target)
  if (url.protocol !== 'http:' || !['127.0.0.1', 'localhost', '[::1]'].includes(url.hostname)
      || url.username || url.password || url.pathname !== '/' || url.search || url.hash || !url.port) {
    throw new Error('VITE_RULE_TASK_PROXY_TARGET must be a loopback HTTP origin with explicit port')
  }
  return { [RULE_TASK_PREFIX]: { target: url.origin, changeOrigin: false } }
}
