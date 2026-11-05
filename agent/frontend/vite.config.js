import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'
import { ruleTaskProxy } from './rule-task-proxy.js'

// 仅进程环境显式打开本机代理；不改路径、不改Cookie范围、不对远端发送学生会话。
const proxy = ruleTaskProxy(process.env.VITE_RULE_TASK_PROXY_TARGET)
export default defineConfig(({ mode }) => ({
  // 构建前统一未知入口值，避免默认构建与兼容输入产生不同的环境替换和产物。
  define: {
    'import.meta.env.VITE_ENTRY_MODE': JSON.stringify(loadEnv(mode, process.cwd()).VITE_ENTRY_MODE === 'replay' ? 'replay' : 'student')
  },
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src')
    }
  },
  server: {
    port: 3000,
    cors: true,
    proxy
  },
  preview: { proxy }
}))
