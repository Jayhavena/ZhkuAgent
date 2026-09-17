<template>
  <div class="home-container">
    <main class="hero">
      <h1 class="title">仲园 <span class="brand">Agent</span></h1>
      <p class="subtitle">校园可信问答 · 本机演示</p>

      <!-- 唯一入口卡：整卡可点，键盘可达。不虚构第二张占位卡——
           当前产品只有任务助手一个应用，版面留白是设计而非缺失 -->
      <div
        class="entry-card"
        role="link"
        tabindex="0"
        @click="navigateTo('/super-agent')"
        @keydown.enter="navigateTo('/super-agent')"
      >
        <div class="card-text">
          <div class="card-title">任务助手</div>
          <div class="card-desc">提交任务，查看处理过程与结果</div>
        </div>
        <div class="card-arrow" aria-hidden="true">→</div>
      </div>
    </main>

    <AppFooter />
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useHead } from '@vueuse/head'
import AppFooter from '../components/AppFooter.vue'

// 设置页面标题和元数据
useHead({
  title: '首页 - 仲园 Agent',
  meta: [
    {
      name: 'description',
      content: '仲园 Agent 本机演示，提供任务助手入口，展示任务处理过程与结果。'
    },
    {
      name: 'keywords',
      content: '仲园 Agent,ZhkuAgent,任务助手,本机演示,首页'
    }
  ]
})

const router = useRouter()

const navigateTo = (path) => {
  router.push(path)
}
</script>

<style scoped>
/* 首页外壳样式说明：
   - 全部取值来自 style.css 的设计令牌层（含暗色自动翻转），不写死色值；
   - 宽度一律 max-width + flex，不写死固定像素，为以后的移动端适配留活口；
   - 设计依据见 docs/design-references/home-redesign-plan.md。 */

.home-container {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background-color: var(--color-page-bg);
  color: var(--color-text-primary);
}

.hero {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
}

.title {
  font-size: 2.5rem;
  font-weight: 500;
  letter-spacing: 1px;
  margin-bottom: 12px;
}

/* 品牌绿的唯一固定落点：克制使用，避免满屏绿色失去"安静"的基调 */
.brand {
  color: var(--color-brand);
}

.subtitle {
  font-size: 1rem;
  color: var(--color-text-secondary);
  letter-spacing: 2px;
  margin-bottom: 48px;
}

.entry-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  width: 100%;
  max-width: 480px;
  padding: 24px 28px;
  background-color: var(--color-surface);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-bubble);
  box-shadow: var(--shadow-card);
  cursor: pointer;
  transition:
    transform var(--duration-hover) ease,
    box-shadow var(--duration-hover) ease,
    border-color var(--duration-hover) ease;
}

/* 唯一的微交互：hover 上浮 + 品牌绿描边。
   不做持续动画——首屏注意力应落在标题与入口上，动效只做反馈不做表演 */
.entry-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-card-hover);
  border-color: var(--color-brand);
}

.entry-card:focus-visible {
  outline: 2px solid var(--color-brand);
  outline-offset: 2px;
}

.card-title {
  font-size: 1.25rem;
  font-weight: 500;
  margin-bottom: 6px;
}

.card-desc {
  font-size: 0.9rem;
  color: var(--color-text-secondary);
}

.card-arrow {
  font-size: 1.5rem;
  color: var(--color-brand);
  flex-shrink: 0;
  transition: transform var(--duration-hover) ease;
}

.entry-card:hover .card-arrow {
  transform: translateX(4px);
}
</style>
