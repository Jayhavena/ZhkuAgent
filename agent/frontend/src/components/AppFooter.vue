<template>
  <footer class="app-footer">
    <div class="footer-content">
      <div class="footer-section">
        <div class="footer-logo">
          <h3>仲园 Agent</h3>
        </div>
        <div class="footer-links">
          <a href="#">《用户协议》</a>
          <a href="#">《隐私政策》</a>
        </div>
      </div>
      
      <div class="footer-section">
        <h4>友情链接</h4>
        <div class="footer-links">
          <a href="https://www.mianshiya.com" target="_blank">面试鸭</a>
          <a href="#" target="_blank">编程导航</a>
          <a href="#" target="_blank">代码小抄</a>
        </div>
      </div>
      
      <div class="footer-section">
        <h4>项目来源</h4>
        <div class="footer-links">
          <span>基于原项目学习与改造</span>
          <span>原项目作者：鱼皮</span>
          <span>原项目：鱼皮AI超级智能体应用平台</span>
        </div>
      </div>
    </div>
    
    <div class="footer-bottom">
      <p>仲园 Agent · 本机演示 · {{ currentYear }}</p>
    </div>
  </footer>
</template>

<script setup>
import { computed } from 'vue'

// 计算当前年份
const currentYear = computed(() => new Date().getFullYear())
</script>

<style scoped>
/* 全部颜色改用设计令牌（style.css），跟随系统明暗自动翻转；
   不写死色值，避免暗色主题下页脚与页面断裂 */
.app-footer {
  background-color: var(--color-surface);
  padding: 40px 0 20px;
  color: var(--color-text-secondary);
  border-top: 1px solid var(--color-border-subtle);
  width: 100%;
  margin-top: auto;
  position: relative;
  z-index: 2;
}

.footer-content {
  max-width: 1200px;
  margin: 0 auto;
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  padding: 0 20px;
}

.footer-section {
  flex: 1;
  min-width: 200px;
  margin-bottom: 30px;
  padding: 0 15px;
}

.footer-logo h3 {
  font-size: 1.25rem;
  margin-bottom: 15px;
  color: var(--color-text-primary);
}

.footer-section h4 {
  font-size: 1rem;
  margin-bottom: 15px;
  color: var(--color-text-primary);
}

.footer-links {
  display: flex;
  flex-direction: column;
}

.footer-links a {
  margin-bottom: 10px;
  color: var(--color-text-secondary);
  text-decoration: none;
  transition: color var(--duration-hover);
}

.footer-links a:hover {
  color: var(--color-brand);
}

.qrcode {
  display: flex;
  align-items: center;
}

.qrcode-container {
  text-align: center;
}

.qrcode-placeholder {
  width: 90px;
  height: 90px;
  background-color: var(--color-bubble-ai-bg);
  margin: 0 auto 10px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 2rem;
}

.qrcode-placeholder:after {
  content: '🤖';
}

.footer-bottom {
  text-align: center;
  padding-top: 20px;
  margin-top: 20px;
  border-top: 1px solid var(--color-border-subtle);
  color: var(--color-text-secondary);
  font-size: 0.9rem;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .footer-content {
    flex-direction: column;
  }

  .footer-section {
    width: 100%;
    margin-bottom: 20px;
    padding: 0;
  }
}

@media (max-width: 480px) {
  .app-footer {
    padding: 30px 0 15px;
  }

  .footer-section h4 {
    font-size: 0.95rem;
  }

  .footer-links a {
    font-size: 0.9rem;
  }

  .qrcode-placeholder {
    width: 80px;
    height: 80px;
  }
}
</style> 