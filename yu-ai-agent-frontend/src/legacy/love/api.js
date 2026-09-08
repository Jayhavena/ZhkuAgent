import { connectSSE } from '../../api'

/**
 * 已下线恋爱应用的专用 API 适配器。
 *
 * <p>默认路由不会导入本文件，因此它不会进入当前发布链路。只有未来明确恢复
 * legacy-love 配置、旧后端入口和独立路由时，才应重新使用该适配器。</p>
 */
export const chatWithLoveApp = (message, chatId) => {
  return connectSSE('/ai/love_app/chat/sse', { message, chatId })
}
