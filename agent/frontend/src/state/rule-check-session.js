import { createControlledSession } from './controlled-session.js'
// 复用换代机制，不共享旧学分入口的主体、Cookie资格或任务持久化。
export const ruleCheckSession = createControlledSession()
