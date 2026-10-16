// 身份是服务端Cookie，同源所有窗口共用一份；任何窗口退出或换身份后，
// 其他窗口内存里的主体和已发布结果都已失效。没有跨窗口通知时，另一个窗口会继续
// 展示旧身份下的任务与结论，所以要显式广播“本窗口已换代”。
const CHANNEL_NAME = 'zhku-agent-identity'
const MESSAGE_TYPE = 'identity-changed'

function defaultChannelFactory() {
  // node/SSR没有同源窗口，且有document才说明是真实浏览器环境；
  // Node自带的BroadcastChannel还会让测试进程无法退出，这里必须降级为单窗口行为。
  if (typeof BroadcastChannel !== 'function' || typeof document === 'undefined') return null
  return new BroadcastChannel(CHANNEL_NAME)
}

export function createIdentityBus({ factory = defaultChannelFactory } = {}) {
  let channel = null
  try { channel = factory() } catch { channel = null }
  const listeners = new Set()
  if (channel) {
    channel.onmessage = event => {
      const data = event?.data
      if (!data || data.type !== MESSAGE_TYPE) return
      for (const listener of [...listeners]) listener(data)
    }
  }
  return {
    available: channel !== null,
    // 广播失败不能反过来影响本窗口已经完成的换代。
    publish(detail = {}) {
      if (!channel) return
      try { channel.postMessage({ type: MESSAGE_TYPE, ...detail }) } catch { /* 忽略跨窗口投递失败 */ }
    },
    subscribe(listener) {
      listeners.add(listener)
      return () => listeners.delete(listener)
    },
    close() {
      listeners.clear()
      const target = channel
      channel = null
      if (target?.onmessage) target.onmessage = null
      try { target?.close() } catch { /* 忽略重复关闭 */ }
    }
  }
}
